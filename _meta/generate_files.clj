#!/usr/bin/env bb

(ns generate-files
  (:require [babashka.fs :as fs]
            [babashka.process :refer [sh]]
            [clojure.string :as str]
            [selmer.parser :as selmer]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json]
            [com.grzm.uri-template :as uri-template]
            [nextjournal.markdown :as md]
            [hiccup2.core :as h])
  (:import [java.time ZonedDateTime LocalDate ZoneId]
           [java.time.format DateTimeFormatter]))

;; ============================================================================
;; Configuration & Constants
;; ============================================================================

(def script-dir
  (if *file*
    (fs/parent *file*)
    (System/getProperty "user.dir")))

(def repo-dir (fs/parent script-dir))

(def posts-per-page 10)
(def index-filename "index.json")
(def tags-index-filename "tags.json")
(def tags-dir-name "tags")

;; ============================================================================
;; Pure Functions - Parsing & String Manipulation
;; ============================================================================

(defn parse-date-from-filename [filename]
  (when-let [[_ date-str] (re-find #"^(\d{4}-\d{2}-\d{2})-" filename)]
    date-str))

(defn parse-lang-from-filename
  "Parse language suffix from filename. Returns nil for default language.
   Example: 2012-12-30-release_todoistCli.en.md -> 'en'
            2012-12-30-release_todoistCli.md -> nil"
  [filename extname]
  (let [pattern (re-pattern (str "\\.([a-z]{2})" (java.util.regex.Pattern/quote extname) "$"))]
    (second (re-find pattern filename))))

(defn remove-lang-suffix
  "Remove language suffix from basename if present"
  [basename lang]
  (if lang
    (str/replace basename (re-pattern (str "\\." lang "$")) "")
    basename))

(defn format-date [date-str]
  (when date-str
    (try
      (let [zdt (ZonedDateTime/parse date-str)]
        (.format zdt DateTimeFormatter/ISO_OFFSET_DATE_TIME))
      (catch Exception _
        date-str))))

(defn date-from-filename [filename]
  (when-let [date-str (parse-date-from-filename filename)]
    (let [local-date (LocalDate/parse date-str)
          zdt (.atStartOfDay local-date (ZoneId/systemDefault))]
      (.format zdt DateTimeFormatter/ISO_OFFSET_DATE_TIME))))

(defn earlier-date
  "Return the earlier of two ISO date strings. If one is nil, return the other."
  [date1 date2]
  (cond
    (nil? date1) date2
    (nil? date2) date1
    :else (let [zdt1 (ZonedDateTime/parse date1)
                zdt2 (ZonedDateTime/parse date2)]
            (if (.isBefore zdt1 zdt2) date1 date2))))

(defn parse-frontmatter [content]
  (let [parts (str/split content #"(?m)^---\s*$" 3)]
    (if (and (>= (count parts) 3) (str/blank? (first parts)))
      (let [yaml-str (second parts)
            body (nth parts 2)]
        {:meta (yaml/parse-string yaml-str)
         :content (str/trim body)})
      {:meta {}
       :content (str/trim content)})))

(defn ensure-tags-vector
  "Ensure tags is a vector of strings"
  [tags]
  (cond
    (nil? tags) []
    (sequential? tags) (mapv str tags)
    :else [(str tags)]))

(defn expand-uri-template
  "Expand a RFC 6570 URI Template with given variables using uri-template library"
  [template vars]
  (let [string-vars (into {} (map (fn [[k v]] [(name k) v]) vars))]
    (uri-template/expand template string-vars)))

(defn local-article-link?
  "Check if a link is a local article reference"
  [href]
  (and href
       (or (str/starts-with? href "./")
           (str/starts-with? href "../")
           (re-find #"^\d{4}-\d{2}-\d{2}-.*\.md$" href))
       (str/ends-with? href ".md")))

;; ============================================================================
;; Pure Functions - URL Generation (take settings as parameter)
;; ============================================================================

(defn github-repo-url
  "Build base GitHub repository URL"
  [github-repo]
  (str "https://github.com/" github-repo "/"))

(defn github-blob-url
  "Build GitHub blob URL for a file"
  [github-repo path]
  (str (github-repo-url github-repo) "blob/master/" path))

(defn github-raw-url
  "Build raw GitHub URL for direct file content access"
  [github-repo path]
  (str "https://raw.githubusercontent.com/" github-repo "/refs/heads/master/" path))

(defn meta-url
  "Build GitHub blob URL for a file in _meta directory"
  [github-repo path]
  (github-blob-url github-repo (str "_meta/" path)))

(defn meta-raw-url
  "Build raw GitHub URL for a file in _meta directory"
  [github-repo path]
  (github-raw-url github-repo (str "_meta/" path)))

(defn post-url
  "Build GitHub blob URL for a post file"
  [github-repo path]
  (github-blob-url github-repo path))

(defn article-url
  "Generate article URL using article_url template.
   If template doesn't start with http, it's relative to base-url."
  [article-url-template base-url github-repo filename lang]
  (if article-url-template
    (let [effective-lang (when (seq lang) lang)
          expanded (expand-uri-template article-url-template
                                        (cond-> {:mdUrl filename}
                                          effective-lang (assoc :lang effective-lang)))]
      (if (str/starts-with? expanded "http")
        expanded
        (let [base (str/replace base-url #"/$" "")
              path (if (str/starts-with? expanded "/")
                     expanded
                     (str "/" expanded))]
          (str base path))))
    (post-url github-repo filename)))

(defn resolve-article-link
  "Convert a local article link to a blog URL"
  [article-url-template base-url github-repo extname href]
  (let [filename (-> href (str/replace #"^\.\.?/" ""))
        lang (parse-lang-from-filename filename extname)]
    (article-url article-url-template base-url github-repo filename lang)))

(defn replace-local-article-links
  "Replace local article links in markdown content with blog URLs"
  [article-url-template base-url github-repo extname md-content]
  (str/replace md-content
               #"\[([^\]]*)\]\(([^)]+\.md)\)"
               (fn [[_ text href :as match]]
                 (if (local-article-link? href)
                   (str "[" text "](" (resolve-article-link article-url-template base-url github-repo extname href) ")")
                   (first match)))))

;; ============================================================================
;; Pure Functions - Pagination & File Naming
;; ============================================================================

(defn paginate
  "Paginate items into groups"
  [items per-page]
  (partition-all per-page items))

(defn page-filename [page-num]
  (if (= page-num 1)
    index-filename
    (str "page-" page-num ".json")))

(defn tag-to-filename
  "Convert tag to safe filename"
  [tag]
  (-> tag
      (str/lower-case)
      (str/replace #"[^a-z0-9\u4e00-\u9fff]+" "-")
      (str/replace #"^-|-$" "")
      (str ".json")))

(defn data-dir-name
  "Get data directory name for a language. nil -> 'data', 'en' -> 'data.en'"
  [lang]
  (if lang
    (str "data." lang)
    "data"))

(defn feed-filename
  "Get feed filename for a language. nil -> 'feed.xml', 'en' -> 'feed.en.xml'"
  [lang]
  (if lang
    (str "feed." lang ".xml")
    "feed.xml"))

;; ============================================================================
;; Pure Functions - Post Metadata Building
;; ============================================================================

(defn build-post-metadata
  "Build post metadata from filename, content, and git dates"
  [settings filename file-content git-dates]
  (let [extname (:extname settings)
        lang (parse-lang-from-filename filename extname)
        basename-with-lang (str/replace filename (re-pattern (str extname "$")) "")
        basename (remove-lang-suffix basename-with-lang lang)
        {:keys [meta content]} (parse-frontmatter file-content)
        default-date (date-from-filename filename)
        date-str (parse-date-from-filename filename)
        title (or (:title meta)
                  (-> basename
                      (str/replace #"^\d{4}-\d{2}-\d{2}-" "")
                      (str/replace "_" " ")))
        tags (ensure-tags-vector (:tags meta))]
    {:id basename
     :path filename
     :title title
     :date date-str
     :lang lang
     :created-at (earlier-date default-date (format-date (:created-at git-dates)))
     :updated-at (or (format-date (:updated-at git-dates)) default-date)
     :tags tags
     :url (post-url (:github-repo settings) filename)
     :raw-content content}))

(defn enrich-post-with-content
  "Add HTML content and blog URL to post metadata"
  [settings post html-content]
  (let [blog-url (article-url (:article-url settings) (:url settings)
                               (:github-repo settings) (:path post) (:lang post))]
    (-> post
        (dissoc :raw-content)
        (assoc :content html-content
               :category (first (:tags post))
               :post-url blog-url
               :id-url (post-url (:github-repo settings) (:id post))))))

;; ============================================================================
;; Pure Functions - Tags & Translations
;; ============================================================================

(defn build-tags-map
  "Build a map of tag -> [posts]"
  [posts]
  (reduce (fn [acc post]
            (reduce (fn [acc2 tag]
                      (update acc2 tag (fnil conj []) post))
                    acc
                    (:tags post)))
          {}
          posts))

(defn build-translations-map
  "Build a map of post id -> [posts with same id but different langs]"
  [all-posts]
  (group-by :id all-posts))

(defn find-translations
  "Find all translations for a post (other language versions)"
  [post translations-map]
  (let [same-id-posts (get translations-map (:id post) [])
        other-langs (filter #(not= (:lang %) (:lang post)) same-id-posts)]
    (mapv (fn [p]
            {:lang (:lang p)
             :title (:title p)
             :path (:path p)
             :url (:url p)
             :tags (:tags p)
             :data-file (str (data-dir-name (:lang p)) "/" index-filename)})
          other-langs)))

(defn add-translations-to-posts
  "Add translations field to each post"
  [posts translations-map]
  (mapv (fn [post]
          (let [translations (find-translations post translations-map)]
            (if (seq translations)
              (assoc post :translations translations)
              post)))
        posts))

;; ============================================================================
;; Pure Functions - Data Structure Building (for JSON/Feed generation)
;; ============================================================================

(defn make-meta
  "Build meta info for JSON output"
  [total-posts total-pages generated-at]
  {:total-posts total-posts
   :total-pages total-pages
   :per-page posts-per-page
   :generated-at generated-at})

(defn build-page-data
  "Build page data structure for JSON output"
  [page-num total-pages page-posts meta-info]
  (let [is-first (= page-num 1)
        is-last (= page-num total-pages)]
    {:meta meta-info
     :pagination {:current-page page-num
                  :has-prev (not is-first)
                  :has-next (not is-last)
                  :prev-file (when-not is-first (page-filename (dec page-num)))
                  :next-file (when-not is-last (page-filename (inc page-num)))}
     :posts (vec page-posts)}))

(defn build-tag-data
  "Build tag data structure for JSON output"
  [tag tag-posts meta-info]
  {:meta meta-info
   :tag tag
   :count (count tag-posts)
   :posts (vec tag-posts)})

(defn build-tags-index-data
  "Build tags index data structure"
  [tags-map meta-info]
  (let [tags-index (for [[tag tag-posts] (sort-by (comp - count second) tags-map)]
                     {:tag tag
                      :count (count tag-posts)
                      :file (str tags-dir-name "/" (tag-to-filename tag))})]
    {:meta meta-info
     :tags (vec tags-index)}))

(defn build-feed-data
  "Build feed data structure for template rendering"
  [settings posts feed-lang updated-at meta-url-val feed-id]
  {:site (assoc settings :updated-at updated-at :lang feed-lang)
   :posts posts
   :meta-url meta-url-val
   :feed-id feed-id})

;; ============================================================================
;; I/O Functions - File System & External Commands
;; ============================================================================

(def read-settings
  "Read and cache settings from settings.yml"
  (memoize
   (fn []
     (let [settings (yaml/parse-string (slurp (fs/file script-dir "settings.yml")))]
       {:extname (get settings :extname ".md")
        :github-repo (get settings :github_repo)
        :url (get settings :url)
        :article-url (get settings :article_url)
        :title (get settings :title)
        :description (get settings :description "")
        :lang (get settings :lang)
        :author-name (get settings :author_name)
        :author-email (get settings :author_email)
        :author-uri (get settings :author_uri)}))))

(defn git-file-dates
  "Get file dates from git history (I/O)"
  [file-path]
  (let [result (sh {:dir (str repo-dir)} "git" "log" "--follow" "--format=%aI" "--" (str file-path))
        dates (when (zero? (:exit result))
                (->> (str/split-lines (:out result))
                     (filter (complement str/blank?))))]
    {:created-at (last dates)
     :updated-at (first dates)}))

(defn markdown->html
  "Convert markdown to HTML using built-in nextjournal.markdown"
  [md-content]
  (let [hiccup (md/->hiccup (md/parse md-content))
        ;; Unwrap outer :div wrapper from nextjournal.markdown
        children (if (and (vector? hiccup) (= :div (first hiccup)))
                   (rest hiccup)
                   [hiccup])]
    (apply str (map #(str (h/html %)) children))))

(defn get-post-files
  "Get all post file paths matching the date pattern (I/O)"
  []
  (let [extname (:extname (read-settings))]
    (->> (fs/glob repo-dir (str "*" extname))
         (map str)
         (filter #(re-find #"^\d{4}-\d{2}-\d{2}-" (fs/file-name %))))))

(defn now-iso
  "Get current timestamp in ISO format (I/O)"
  []
  (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME))

;; ============================================================================
;; High-level I/O Functions
;; ============================================================================

(defn get-post-metadata
  "Get post metadata without content (for JSON generation)"
  [file-path]
  (let [settings (read-settings)
        metadata (build-post-metadata settings (fs/file-name file-path) (slurp file-path) (git-file-dates file-path))]
    (dissoc metadata :raw-content)))

(defn get-post-with-content
  "Get post with HTML content (for feed generation)"
  [file-path]
  (let [settings (read-settings)
        file-content (slurp file-path)
        metadata (build-post-metadata settings (fs/file-name file-path) file-content (git-file-dates file-path))
        processed-md (replace-local-article-links (:article-url settings) (:url settings)
                                                   (:github-repo settings) (:extname settings)
                                                   (:raw-content metadata))
        html-content (markdown->html processed-md)]
    (enrich-post-with-content settings metadata html-content)))

(defn get-all-posts
  "Get all posts sorted by updated-at (newest first)"
  []
  (->> (get-post-files)
       (map get-post-metadata)
       (sort-by :updated-at #(compare %2 %1))))

(defn get-posts-for-feed
  "Get all posts with content for feed generation, sorted by created-at (newest first)"
  []
  (->> (get-post-files)
       (map get-post-with-content)
       (sort-by :created-at #(compare %2 %1))))

;; ============================================================================
;; File Generation Functions (I/O)
;; ============================================================================

(defn write-json-file [path data]
  (spit path (json/generate-string data {:pretty true})))

(defn generate-page-files
  "Generate paginated JSON files for posts"
  [data-dir pages meta-info]
  (let [total-pages (count pages)]
    (doseq [[page-idx page-posts] (map-indexed vector pages)]
      (let [page-num (inc page-idx)
            page-data (build-page-data page-num total-pages page-posts meta-info)]
        (write-json-file (fs/file data-dir (page-filename page-num)) page-data)))))

(defn generate-tag-files
  "Generate individual tag JSON files"
  [tags-dir tags-map meta-info]
  (doseq [[tag tag-posts] tags-map]
    (let [tag-data (build-tag-data tag tag-posts meta-info)]
      (write-json-file (fs/file tags-dir (tag-to-filename tag)) tag-data))))

(defn generate-tags-index
  "Generate tags index file listing all tags"
  [data-dir tags-map meta-info]
  (let [index-data (build-tags-index-data tags-map meta-info)]
    (write-json-file (fs/file data-dir tags-index-filename) index-data)))

(defn generate-posts-json-for-lang
  "Generate paginated JSON files for posts of a specific language"
  [all-posts translations-map lang]
  (let [posts-raw (filter #(= (:lang %) lang) all-posts)
        posts (add-translations-to-posts posts-raw translations-map)
        total-posts (count posts)
        pages (paginate posts posts-per-page)
        total-pages (count pages)
        meta-info (make-meta total-posts total-pages (now-iso))
        data-dir (fs/file script-dir (data-dir-name lang))]
    (when (pos? total-posts)
      (fs/create-dirs data-dir)
      (generate-page-files data-dir pages meta-info)
      (let [tags-dir (fs/file data-dir tags-dir-name)
            tags-map (build-tags-map posts)]
        (fs/create-dirs tags-dir)
        (generate-tag-files tags-dir tags-map meta-info)
        (generate-tags-index data-dir tags-map meta-info)
        {:lang lang :pages total-pages :tags (count tags-map)}))))

(defn generate-posts-json
  "Generate paginated JSON files for posts, grouped by language"
  []
  (let [all-posts (get-all-posts)
        translations-map (build-translations-map all-posts)
        langs (distinct (map :lang all-posts))
        results (keep #(generate-posts-json-for-lang all-posts translations-map %) langs)]
    (doseq [{:keys [lang pages tags]} results]
      (println (str "Generated " pages " page files + " tags " tag files in " (data-dir-name lang) "/ directory")))))

(defn generate-feed-for-lang
  "Generate Atom feed XML for a specific language"
  [all-posts lang]
  (let [posts (->> all-posts
                   (filter #(= (:lang %) lang))
                   (take 10))
        settings (read-settings)
        feed-lang (or lang (:lang settings))
        github-repo (:github-repo settings)]
    (when (seq posts)
      (let [updated-at (or (:updated-at (first posts)) (now-iso))
            template (slurp (fs/file script-dir "feed.xml.selmer"))
            feed-data (build-feed-data settings posts feed-lang updated-at
                                       (meta-raw-url github-repo "feed.xslt.xml")
                                       (github-repo-url github-repo))
            feed-content (selmer/render template feed-data)]
        (spit (fs/file script-dir (feed-filename lang)) feed-content)
        (feed-filename lang)))))

(defn generate-feed
  "Generate Atom feed XML files for all languages"
  []
  (let [all-posts (get-posts-for-feed)
        langs (distinct (map :lang all-posts))
        results (keep #(generate-feed-for-lang all-posts %) langs)]
    (println (str "Generated feed files: " (str/join ", " results)))))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn -main []
  (generate-feed)
  (generate-posts-json))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
