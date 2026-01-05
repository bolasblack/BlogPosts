#!/usr/bin/env bb

(ns generate-files
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell sh]]
            [clojure.string :as str]
            [selmer.parser :as selmer]
            [clj-yaml.core :as yaml]
            [cheshire.core :as json])
  (:import [java.time ZonedDateTime LocalDate ZoneId]
           [java.time.format DateTimeFormatter]))

(def script-dir
  (if *file*
    (fs/parent *file*)
    (System/getProperty "user.dir")))

(def repo-dir (fs/parent script-dir))
(def posts-per-page 10)

(defn read-settings []
  (let [settings (yaml/parse-string (slurp (fs/file script-dir "settings.yml")))]
    {:extname (get settings :extname ".md")
     :github-repo (get settings :github_repo)
     :url (get settings :url)
     :title (get settings :title)
     :description (get settings :description "")
     :lang (get settings :lang)
     :author-name (get settings :author_name)
     :author-email (get settings :author_email)
     :author-uri (get settings :author_uri)}))

(defn meta-url [path]
  (let [settings (read-settings)]
    (str "https://github.com/" (:github-repo settings) "/blob/master/_meta/" path)))

(defn post-url [path]
  (let [settings (read-settings)]
    (str "https://github.com/" (:github-repo settings) "/blob/master/" path)))

(defn parse-date-from-filename [filename]
  (when-let [[_ date-str] (re-find #"^(\d{4}-\d{2}-\d{2})-" filename)]
    date-str))

(defn parse-lang-from-filename [filename extname]
  "Parse language suffix from filename. Returns nil for default language.
   Example: 2012-12-30-release_todoistCli.en.md -> 'en'
            2012-12-30-release_todoistCli.md -> nil"
  (let [pattern (re-pattern (str "\\.([a-z]{2})" (java.util.regex.Pattern/quote extname) "$"))]
    (second (re-find pattern filename))))

(defn remove-lang-suffix [basename lang]
  "Remove language suffix from basename if present"
  (if lang
    (str/replace basename (re-pattern (str "\\." lang "$")) "")
    basename))

(defn git-file-dates [file-path]
  (let [result (sh {:dir (str repo-dir)} "git" "log" "--follow" "--format=%aI" "--" (str file-path))
        dates (when (zero? (:exit result))
                (->> (str/split-lines (:out result))
                     (filter (complement str/blank?))))]
    {:created-at (last dates)
     :updated-at (first dates)}))

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

(defn earlier-date [date1 date2]
  "Return the earlier of two ISO date strings. If one is nil, return the other."
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

(defn markdown-to-html [md-content]
  (let [result (sh {:in md-content} "pandoc" "-f" "gfm" "-t" "html")]
    (if (zero? (:exit result))
      (:out result)
      md-content)))

(defn ensure-tags-vector [tags]
  "Ensure tags is a vector of strings"
  (cond
    (nil? tags) []
    (sequential? tags) (vec (map str tags))
    :else [(str tags)]))

(defn get-post-metadata [file-path]
  "Get post metadata without content (for JSON generation)"
  (let [settings (read-settings)
        filename (fs/file-name file-path)
        extname (:extname settings)
        lang (parse-lang-from-filename filename extname)
        basename-with-lang (str/replace filename (re-pattern (str extname "$")) "")
        basename (remove-lang-suffix basename-with-lang lang)
        content (slurp file-path)
        {:keys [meta]} (parse-frontmatter content)
        git-dates (git-file-dates file-path)
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
     :url (post-url filename)}))

(defn get-post-with-content [file-path]
  "Get post with HTML content (for feed generation)"
  (let [metadata (get-post-metadata file-path)
        content (slurp file-path)
        {:keys [content]} (parse-frontmatter content)
        html-content (markdown-to-html content)]
    (assoc metadata
           :content html-content
           :category (first (:tags metadata)) ;; for feed compatibility
           :post-url (:url metadata)
           :id-url (post-url (:id metadata)))))

(defn get-all-posts []
  "Get all posts sorted by updated-at (newest first)"
  (let [settings (read-settings)
        extname (:extname settings)
        md-files (->> (fs/glob repo-dir (str "*" extname))
                      (map str)
                      (filter #(re-find #"^\d{4}-\d{2}-\d{2}-" (fs/file-name %))))]
    (->> md-files
         (map get-post-metadata)
         (sort-by :updated-at)
         reverse)))

(defn get-posts-for-feed []
  "Get all posts with content for feed generation"
  (let [settings (read-settings)
        extname (:extname settings)
        md-files (->> (fs/glob repo-dir (str "*" extname))
                      (map str)
                      (filter #(re-find #"^\d{4}-\d{2}-\d{2}-" (fs/file-name %))))]
    (->> md-files
         (map get-post-with-content)
         (sort-by :updated-at)
         reverse)))

(defn group-posts-by-lang [posts]
  "Group posts by language. nil lang means default language."
  (group-by :lang posts))

(defn paginate [items per-page]
  "Paginate items into groups"
  (partition-all per-page items))

(defn page-filename [page-num]
  (if (= page-num 1)
    "index.json"
    (str "page-" page-num ".json")))

(defn make-meta [total-posts total-pages]
  {:total-posts total-posts
   :total-pages total-pages
   :per-page posts-per-page
   :generated-at (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME)})

(defn build-tags-map [posts]
  "Build a map of tag -> [posts]"
  (reduce (fn [acc post]
            (reduce (fn [acc2 tag]
                      (update acc2 tag (fnil conj []) post))
                    acc
                    (:tags post)))
          {}
          posts))

(defn tag-to-filename [tag]
  "Convert tag to safe filename"
  (-> tag
      (str/lower-case)
      (str/replace #"[^a-z0-9\u4e00-\u9fff]+" "-")
      (str/replace #"^-|-$" "")
      (str ".json")))

(defn data-dir-name [lang]
  "Get data directory name for a language. nil -> 'data', 'en' -> 'data.en'"
  (if lang
    (str "data." lang)
    "data"))

(defn build-translations-map [all-posts]
  "Build a map of post id -> [posts with same id but different langs]"
  (group-by :id all-posts))

(defn find-translations [post translations-map]
  "Find all translations for a post (other language versions)"
  (let [same-id-posts (get translations-map (:id post) [])
        other-langs (filter #(not= (:lang %) (:lang post)) same-id-posts)]
    (mapv (fn [p]
            {:lang (:lang p)
             :title (:title p)
             :path (:path p)
             :url (:url p)
             :tags (:tags p)
             :data-file (str (data-dir-name (:lang p)) "/index.json")})
          other-langs)))

(defn add-translations-to-posts [posts translations-map]
  "Add translations field to each post"
  (mapv (fn [post]
          (let [translations (find-translations post translations-map)]
            (if (seq translations)
              (assoc post :translations translations)
              post)))
        posts))

(defn generate-posts-json-for-lang [all-posts translations-map lang]
  "Generate paginated JSON files for posts of a specific language"
  (let [posts-raw (filter #(= (:lang %) lang) all-posts)
        posts (add-translations-to-posts posts-raw translations-map)
        total-posts (count posts)
        pages (paginate posts posts-per-page)
        total-pages (count pages)
        meta-info (make-meta total-posts total-pages)
        data-dir (fs/file script-dir (data-dir-name lang))]
    (when (pos? total-posts)
      ;; Create data directory if not exists
      (fs/create-dirs data-dir)
      ;; Generate each page
      (doseq [[page-idx page-posts] (map-indexed vector pages)]
        (let [page-num (inc page-idx)
              is-first (= page-num 1)
              is-last (= page-num total-pages)
              page-data {:meta meta-info
                         :pagination {:current-page page-num
                                      :has-prev (not is-first)
                                      :has-next (not is-last)
                                      :prev-file (when (not is-first)
                                                   (page-filename (dec page-num)))
                                      :next-file (when (not is-last)
                                                   (page-filename (inc page-num)))}
                         :posts (vec page-posts)}
              filename (page-filename page-num)]
          (spit (fs/file data-dir filename)
                (json/generate-string page-data {:pretty true}))))
      ;; Generate per-tag JSON files
      (let [tags-dir (fs/file data-dir "tags")
            tags-map (build-tags-map posts)
            tag-count (count tags-map)]
        (fs/create-dirs tags-dir)
        ;; Generate individual tag files
        (doseq [[tag tag-posts] tags-map]
          (let [filename (tag-to-filename tag)
                tag-data {:meta meta-info
                          :tag tag
                          :count (count tag-posts)
                          :posts (vec tag-posts)}]
            (spit (fs/file tags-dir filename)
                  (json/generate-string tag-data {:pretty true}))))
        ;; Generate tags index (list of all tags with counts and filenames)
        (let [tags-index (for [[tag tag-posts] (sort-by (comp - count second) tags-map)]
                           {:tag tag
                            :count (count tag-posts)
                            :file (str "tags/" (tag-to-filename tag))})
              index-data {:meta meta-info
                          :tags (vec tags-index)}]
          (spit (fs/file data-dir "tags.json")
                (json/generate-string index-data {:pretty true})))
        {:lang lang :pages total-pages :tags tag-count}))))

(defn generate-posts-json []
  "Generate paginated JSON files for posts, grouped by language"
  (let [all-posts (get-all-posts)
        translations-map (build-translations-map all-posts)
        langs (distinct (map :lang all-posts))
        results (keep #(generate-posts-json-for-lang all-posts translations-map %) langs)]
    (doseq [{:keys [lang pages tags]} results]
      (println (str "Generated " pages " page files + " tags " tag files in " (data-dir-name lang) "/ directory")))))

(defn feed-filename [lang]
  "Get feed filename for a language. nil -> 'feed.xml', 'en' -> 'feed.en.xml'"
  (if lang
    (str "feed." lang ".xml")
    "feed.xml"))

(defn generate-feed-for-lang [all-posts lang]
  "Generate Atom feed XML for a specific language"
  (let [posts (->> all-posts
                   (filter #(= (:lang %) lang))
                   (take 10))
        settings (read-settings)
        feed-lang (or lang (:lang settings))]
    (when (seq posts)
      (let [updated-at (or (:updated-at (first posts))
                           (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME))
            template (slurp (fs/file script-dir "feed.xml.selmer"))
            feed-content (selmer/render template
                                        {:site (assoc settings
                                                      :updated-at updated-at
                                                      :lang feed-lang)
                                         :posts posts
                                         :meta-url (meta-url "feed.xslt.xml")
                                         :feed-id (str (meta-url "") "/")})]
        (spit (fs/file script-dir (feed-filename lang)) feed-content)
        (feed-filename lang)))))

(defn generate-feed []
  "Generate Atom feed XML files for all languages"
  (let [all-posts (get-posts-for-feed)
        langs (distinct (map :lang all-posts))
        results (keep #(generate-feed-for-lang all-posts %) langs)]
    (println (str "Generated feed files: " (str/join ", " results)))))

(defn -main []
  (generate-feed)
  (generate-posts-json))

(-main)
