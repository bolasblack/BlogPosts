#!/usr/bin/env bb

(ns generate-feed
  (:require [babashka.fs :as fs]
            [babashka.process :refer [shell sh]]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [selmer.parser :as selmer]
            [clj-yaml.core :as yaml])
  (:import [java.time ZonedDateTime LocalDate ZoneId]
           [java.time.format DateTimeFormatter]))

(def script-dir
  (if *file*
    (fs/parent *file*)
    (System/getProperty "user.dir")))

(def repo-dir (fs/parent script-dir))

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

(defn get-posts []
  (let [settings (read-settings)
        extname (:extname settings)
        md-files (->> (fs/glob repo-dir (str "*" extname))
                      (map str)
                      (filter #(re-find #"^\d{4}-\d{2}-\d{2}-" (fs/file-name %))))]
    (->> md-files
         (map (fn [file-path]
                (let [filename (fs/file-name file-path)
                      basename (str/replace filename (re-pattern (str extname "$")) "")
                      content (slurp file-path)
                      {:keys [meta content]} (parse-frontmatter content)
                      git-dates (git-file-dates file-path)
                      default-date (date-from-filename filename)
                      title (or (:title meta)
                                (-> basename
                                    (str/replace #"^\d{4}-\d{2}-\d{2}-" "")
                                    (str/replace "_" " ")))
                      html-content (markdown-to-html content)]
                  {:id basename
                   :url filename
                   :title title
                   :content html-content
                   :post-url (post-url filename)
                   :id-url (post-url basename)
                   :created-at (or (format-date (:created-at git-dates)) default-date)
                   :updated-at (or (format-date (:updated-at git-dates)) default-date)
                   :category (first (:category meta))
                   :tags (:tags meta)
                   :lang (:lang meta)})))
         (sort-by :updated-at)
         reverse
         (take 10))))

(defn generate-feed []
  (let [settings (read-settings)
        posts (get-posts)
        updated-at (or (:updated-at (first posts))
                       (.format (ZonedDateTime/now) DateTimeFormatter/ISO_OFFSET_DATE_TIME))
        template (slurp (fs/file script-dir "feed.xml.selmer"))
        feed-content (selmer/render template
                                    {:site (assoc settings :updated-at updated-at)
                                     :posts posts
                                     :meta-url (meta-url "feed.xslt.xml")
                                     :feed-id (str (meta-url "") "/")})]
    (spit (fs/file script-dir "feed.xml") feed-content)
    (println "feed.xml generated successfully")))

(generate-feed)
