#!/usr/bin/env bb

(ns generate-files-test
  (:require [clojure.test :refer [deftest testing is run-tests]]
            [clojure.string :as str]
            [babashka.fs :as fs]))

;; Load the functions from generate_files.clj
(def script-dir (fs/parent *file*))
(load-file (str (fs/file script-dir "generate_files.clj")))

;; ============================================================================
;; Tests for Parsing & String Manipulation
;; ============================================================================

(deftest test-parse-date-from-filename
  (testing "extracts date from standard filename"
    (is (= "2024-01-15" (generate-files/parse-date-from-filename "2024-01-15-my-post.md")))
    (is (= "2012-12-30" (generate-files/parse-date-from-filename "2012-12-30-release_todoistCli.md"))))
  (testing "returns nil for filenames without date prefix"
    (is (nil? (generate-files/parse-date-from-filename "README.md")))
    (is (nil? (generate-files/parse-date-from-filename "my-post.md"))))
  (testing "handles edge cases"
    (is (= "2024-01-01" (generate-files/parse-date-from-filename "2024-01-01-.md")))
    (is (nil? (generate-files/parse-date-from-filename "24-01-15-post.md")))))

(deftest test-parse-lang-from-filename
  (testing "extracts language suffix"
    (is (= "en" (generate-files/parse-lang-from-filename "2024-01-15-post.en.md" ".md")))
    (is (= "zh" (generate-files/parse-lang-from-filename "2024-01-15-post.zh.md" ".md"))))
  (testing "returns nil for default language (no suffix)"
    (is (nil? (generate-files/parse-lang-from-filename "2024-01-15-post.md" ".md"))))
  (testing "handles different extensions"
    (is (= "en" (generate-files/parse-lang-from-filename "post.en.markdown" ".markdown"))))
  (testing "does not match non-language suffixes"
    (is (nil? (generate-files/parse-lang-from-filename "2024-01-15-v1.0.md" ".md")))
    (is (nil? (generate-files/parse-lang-from-filename "2024-01-15-post.abc.md" ".md")))))

(deftest test-remove-lang-suffix
  (testing "removes language suffix"
    (is (= "2024-01-15-post" (generate-files/remove-lang-suffix "2024-01-15-post.en" "en")))
    (is (= "my-post" (generate-files/remove-lang-suffix "my-post.zh" "zh"))))
  (testing "returns original when no lang"
    (is (= "2024-01-15-post" (generate-files/remove-lang-suffix "2024-01-15-post" nil))))
  (testing "returns original when lang not at end"
    (is (= "post.en.extra" (generate-files/remove-lang-suffix "post.en.extra" "en")))))

(deftest test-format-date
  (testing "validates and returns well-formed ISO date unchanged"
    (is (= "2024-01-15T00:00:00+08:00"
           (generate-files/format-date "2024-01-15T00:00:00+08:00")))
    (is (= "2024-01-15T00:00:00Z"
           (generate-files/format-date "2024-01-15T00:00:00Z"))))
  (testing "returns nil for nil input"
    (is (nil? (generate-files/format-date nil))))
  (testing "returns original for invalid date (fallback)"
    (is (= "not-a-date" (generate-files/format-date "not-a-date")))))

(deftest test-date-from-filename
  (testing "converts date from filename to ISO format"
    (let [result (generate-files/date-from-filename "2024-01-15-post.md")]
      ;; Verify complete ISO 8601 format with timezone offset
      (is (re-matches #"2024-01-15T00:00:00(?:Z|[+-]\d{2}:\d{2})" result))))
  (testing "returns nil for non-dated filename"
    (is (nil? (generate-files/date-from-filename "README.md")))))

(deftest test-earlier-date
  (testing "returns earlier date"
    (is (= "2024-01-01T00:00:00+08:00"
           (generate-files/earlier-date "2024-01-01T00:00:00+08:00" "2024-01-15T00:00:00+08:00")))
    (is (= "2024-01-01T00:00:00+08:00"
           (generate-files/earlier-date "2024-01-15T00:00:00+08:00" "2024-01-01T00:00:00+08:00"))))
  (testing "handles nil values"
    (is (= "2024-01-01T00:00:00+08:00"
           (generate-files/earlier-date nil "2024-01-01T00:00:00+08:00")))
    (is (= "2024-01-01T00:00:00+08:00"
           (generate-files/earlier-date "2024-01-01T00:00:00+08:00" nil)))
    (is (nil? (generate-files/earlier-date nil nil)))))

(deftest test-parse-frontmatter
  (testing "parses YAML frontmatter"
    (let [content "---\ntitle: My Post\ntags:\n  - clojure\n  - testing\n---\n\nPost content here"
          result (generate-files/parse-frontmatter content)]
      (is (= "My Post" (get-in result [:meta :title])))
      (is (= ["clojure" "testing"] (get-in result [:meta :tags])))
      (is (= "Post content here" (:content result)))))
  (testing "handles content without frontmatter"
    (let [result (generate-files/parse-frontmatter "Just plain content")]
      (is (= {} (:meta result)))
      (is (= "Just plain content" (:content result)))))
  (testing "handles empty frontmatter"
    (let [result (generate-files/parse-frontmatter "---\n---\n\nContent")]
      (is (nil? (:meta result)))
      (is (= "Content" (:content result))))))

(deftest test-ensure-tags-vector
  (testing "returns empty vector for nil"
    (is (= [] (generate-files/ensure-tags-vector nil))))
  (testing "converts sequence to vector of strings"
    (is (= ["a" "b" "c"] (generate-files/ensure-tags-vector ["a" "b" "c"])))
    (is (= ["a" "b"] (generate-files/ensure-tags-vector '("a" "b")))))
  (testing "wraps single value in vector"
    (is (= ["tag"] (generate-files/ensure-tags-vector "tag"))))
  (testing "converts non-string values to strings"
    (is (= ["1" "2"] (generate-files/ensure-tags-vector [1 2])))))

(deftest test-expand-uri-template
  (testing "expands simple variables"
    (is (= "https://example.com/posts/my-post.md"
           (generate-files/expand-uri-template "https://example.com/posts/{mdUrl}" {:mdUrl "my-post.md"}))))
  (testing "expands multiple variables"
    (is (= "https://example.com/en/posts/my-post.md"
           (generate-files/expand-uri-template "https://example.com/{lang}/posts/{mdUrl}"
                                               {:lang "en" :mdUrl "my-post.md"}))))
  (testing "handles missing variables"
    (is (= "https://example.com/posts/"
           (generate-files/expand-uri-template "https://example.com/posts/{mdUrl}" {})))))

(deftest test-local-article-link?
  (testing "detects relative links starting with ./"
    (is (true? (generate-files/local-article-link? "./2024-01-15-post.md")))
    (is (true? (generate-files/local-article-link? "./other-post.md"))))
  (testing "detects relative links starting with ../"
    (is (true? (generate-files/local-article-link? "../2024-01-15-post.md"))))
  (testing "detects bare filename with date pattern"
    (is (true? (generate-files/local-article-link? "2024-01-15-my-post.md"))))
  (testing "rejects absolute URLs"
    (is (not (generate-files/local-article-link? "https://example.com/post.md")))
    (is (not (generate-files/local-article-link? "http://example.com/post.md"))))
  (testing "rejects non-.md files"
    (is (not (generate-files/local-article-link? "./image.png")))
    (is (not (generate-files/local-article-link? "./script.js"))))
  (testing "rejects nil"
    (is (not (generate-files/local-article-link? nil)))))

;; ============================================================================
;; Tests for Pure URL Functions
;; ============================================================================

(deftest test-github-repo-url
  (testing "builds GitHub repository URL"
    (is (= "https://github.com/user/repo/"
           (generate-files/github-repo-url "user/repo")))))

(deftest test-github-blob-url
  (testing "builds GitHub blob URL"
    (is (= "https://github.com/user/repo/blob/master/path/file.md"
           (generate-files/github-blob-url "user/repo" "path/file.md")))
    (is (= "https://github.com/user/repo/blob/master/file.md"
           (generate-files/github-blob-url "user/repo" "file.md")))))

(deftest test-meta-url
  (testing "builds meta URL"
    (is (= "https://github.com/user/repo/blob/master/_meta/feed.xml"
           (generate-files/meta-url "user/repo" "feed.xml")))))

(deftest test-post-url
  (testing "builds post URL"
    (is (= "https://github.com/user/repo/blob/master/2024-01-15-post.md"
           (generate-files/post-url "user/repo" "2024-01-15-post.md")))))

(deftest test-article-url
  (testing "expands article URL template"
    (is (= "https://blog.example.com/#/goto/articles/post.md"
           (generate-files/article-url "#/goto/articles/{mdUrl}" "https://blog.example.com" "user/repo" "post.md" nil))))
  (testing "handles relative template (no http)"
    (is (= "https://blog.example.com/#/goto/articles/post.md"
           (generate-files/article-url "#/goto/articles/{mdUrl}" "https://blog.example.com/" "user/repo" "post.md" nil))))
  (testing "includes lang in template"
    (is (= "https://blog.example.com/en/posts/post.md"
           (generate-files/article-url "/{lang}/posts/{mdUrl}" "https://blog.example.com" "user/repo" "post.md" "en"))))
  (testing "falls back to GitHub URL when no template"
    (is (= "https://github.com/user/repo/blob/master/post.md"
           (generate-files/article-url nil "https://blog.example.com" "user/repo" "post.md" nil)))))

(deftest test-resolve-article-link
  (testing "resolves relative link"
    (is (= "https://blog.example.com/#/goto/articles/2024-01-15-post.md"
           (generate-files/resolve-article-link "#/goto/articles/{mdUrl}" "https://blog.example.com" "user/repo" ".md" "./2024-01-15-post.md"))))
  (testing "extracts lang from filename"
    (is (= "https://blog.example.com/en/posts/2024-01-15-post.en.md"
           (generate-files/resolve-article-link "/{lang}/posts/{mdUrl}" "https://blog.example.com" "user/repo" ".md" "./2024-01-15-post.en.md")))))

(deftest test-replace-local-article-links
  (testing "replaces local links in markdown"
    (let [md "Check out [my post](./2024-01-15-post.md) for details."
          result (generate-files/replace-local-article-links "#/goto/{mdUrl}" "https://blog.example.com" "user/repo" ".md" md)]
      (is (= "Check out [my post](https://blog.example.com/#/goto/2024-01-15-post.md) for details." result))))
  (testing "preserves external links"
    (let [md "Check out [external](https://example.com/post.md) for details."]
      (is (= md (generate-files/replace-local-article-links "#/goto/{mdUrl}" "https://blog.example.com" "user/repo" ".md" md))))))

;; ============================================================================
;; Tests for Pagination & File Naming
;; ============================================================================

(deftest test-paginate
  (testing "paginates items correctly"
    (is (= [[1 2] [3 4] [5]] (vec (map vec (generate-files/paginate [1 2 3 4 5] 2)))))
    (is (= [[1 2 3]] (vec (map vec (generate-files/paginate [1 2 3] 5))))))
  (testing "handles empty collection"
    (is (= [] (vec (generate-files/paginate [] 5)))))
  (testing "handles exact division"
    (is (= [[1 2] [3 4]] (vec (map vec (generate-files/paginate [1 2 3 4] 2)))))))

(deftest test-page-filename
  (testing "first page uses index.json"
    (is (= "index.json" (generate-files/page-filename 1))))
  (testing "other pages use page-N.json"
    (is (= "page-2.json" (generate-files/page-filename 2)))
    (is (= "page-10.json" (generate-files/page-filename 10)))))

(deftest test-tag-to-filename
  (testing "converts tag to lowercase filename"
    (is (= "javascript.json" (generate-files/tag-to-filename "JavaScript")))
    (is (= "clojure.json" (generate-files/tag-to-filename "clojure"))))
  (testing "replaces special characters with hyphens"
    (is (= "c.json" (generate-files/tag-to-filename "C++")))
    (is (= "node-js.json" (generate-files/tag-to-filename "Node.js"))))
  (testing "preserves Chinese characters"
    (is (= "中文标签.json" (generate-files/tag-to-filename "中文标签")))))

(deftest test-data-dir-name
  (testing "default language uses 'data'"
    (is (= "data" (generate-files/data-dir-name nil))))
  (testing "other languages use 'data.{lang}'"
    (is (= "data.en" (generate-files/data-dir-name "en")))
    (is (= "data.zh" (generate-files/data-dir-name "zh")))))

(deftest test-feed-filename
  (testing "default language uses 'feed.xml'"
    (is (= "feed.xml" (generate-files/feed-filename nil))))
  (testing "other languages use 'feed.{lang}.xml'"
    (is (= "feed.en.xml" (generate-files/feed-filename "en")))
    (is (= "feed.zh.xml" (generate-files/feed-filename "zh")))))

;; ============================================================================
;; Tests for Post Metadata Building
;; ============================================================================

(deftest test-build-post-metadata
  (testing "builds metadata from content"
    (let [settings {:extname ".md" :github-repo "user/repo"}
          content "---\ntitle: My Post\ntags:\n  - clojure\n---\n\nContent here"
          ;; Use date earlier than any timezone's midnight on 2024-01-15 to ensure git date is used
          git-dates {:created-at "2024-01-14T00:00:00+00:00" :updated-at "2024-01-20T10:00:00+08:00"}
          result (generate-files/build-post-metadata settings "2024-01-15-my-post.md" content git-dates)]
      (is (= "2024-01-15-my-post" (:id result)))
      (is (= "2024-01-15-my-post.md" (:path result)))
      (is (= "My Post" (:title result)))
      (is (= "2024-01-15" (:date result)))
      (is (nil? (:lang result)))
      (is (= ["clojure"] (:tags result)))
      (is (= "Content here" (:raw-content result)))
      (is (= "2024-01-14T00:00:00Z" (:created-at result)))
      (is (= "2024-01-20T10:00:00+08:00" (:updated-at result)))
      (is (= "https://github.com/user/repo/blob/master/2024-01-15-my-post.md" (:url result)))))
  (testing "extracts language from filename"
    (let [settings {:extname ".md" :github-repo "user/repo"}
          content "---\ntitle: EN Post\n---\n\nContent"
          git-dates {}
          result (generate-files/build-post-metadata settings "2024-01-15-post.en.md" content git-dates)]
      (is (= "en" (:lang result)))
      (is (= "2024-01-15-post" (:id result)))))
  (testing "generates title from filename when not in frontmatter"
    (let [settings {:extname ".md" :github-repo "user/repo"}
          content "No frontmatter"
          git-dates {}
          result (generate-files/build-post-metadata settings "2024-01-15-my_awesome_post.md" content git-dates)]
      (is (= "my awesome post" (:title result))))))

(deftest test-enrich-post-with-content
  (testing "adds content and URLs to post"
    (let [settings {:article-url "#/goto/{mdUrl}" :url "https://blog.example.com" :github-repo "user/repo"}
          post {:id "my-post" :path "my-post.md" :lang nil :tags ["test"] :raw-content "raw"}
          result (generate-files/enrich-post-with-content settings post "<p>HTML</p>")]
      (is (= "<p>HTML</p>" (:content result)))
      (is (= "test" (:category result)))
      (is (= "https://blog.example.com/#/goto/my-post.md" (:post-url result)))
      (is (= "https://github.com/user/repo/blob/master/my-post" (:id-url result)))
      (is (nil? (:raw-content result))))))

;; ============================================================================
;; Tests for Tags & Translations
;; ============================================================================

(deftest test-build-tags-map
  (testing "builds tag to posts mapping"
    (let [p1 {:id "p1" :tags ["a" "b"]}
          p2 {:id "p2" :tags ["b" "c"]}
          p3 {:id "p3" :tags ["a"]}
          result (generate-files/build-tags-map [p1 p2 p3])]
      (is (= [p1 p3] (get result "a")))
      (is (= [p1 p2] (get result "b")))
      (is (= [p2] (get result "c")))))
  (testing "handles posts without tags"
    (let [posts [{:id "p1" :tags []}]
          result (generate-files/build-tags-map posts)]
      (is (= {} result))))
  (testing "handles empty posts"
    (is (= {} (generate-files/build-tags-map [])))))

(deftest test-build-translations-map
  (testing "groups posts by id"
    (let [p1-default {:id "p1" :lang nil}
          p1-en {:id "p1" :lang "en"}
          p2 {:id "p2" :lang nil}
          result (generate-files/build-translations-map [p1-default p1-en p2])]
      (is (= [p1-default p1-en] (get result "p1")))
      (is (= [p2] (get result "p2"))))))

(deftest test-find-translations
  (testing "finds translations of a post with complete structure"
    (let [post {:id "my-post" :lang nil :title "My Post" :path "my-post.md" :url "url1" :tags ["a"]}
          translations-map {"my-post" [post
                                       {:id "my-post" :lang "en" :title "My Post EN" :path "my-post.en.md" :url "url2" :tags ["b"]}
                                       {:id "my-post" :lang "zh" :title "My Post ZH" :path "my-post.zh.md" :url "url3" :tags ["c"]}]}
          result (generate-files/find-translations post translations-map)]
      (is (= 2 (count result)))
      ;; Verify complete structure of each translation
      (is (= #{{:lang "en" :title "My Post EN" :path "my-post.en.md" :url "url2" :tags ["b"] :data-file "data.en/index.json"}
               {:lang "zh" :title "My Post ZH" :path "my-post.zh.md" :url "url3" :tags ["c"] :data-file "data.zh/index.json"}}
             (set result)))))
  (testing "returns empty vector when no translations"
    (let [post {:id "my-post" :lang nil}
          translations-map {"my-post" [post]}
          result (generate-files/find-translations post translations-map)]
      (is (= [] result))))
  (testing "excludes current language version"
    (let [post {:id "my-post" :lang "en" :title "EN" :path "p.en.md" :url "u1" :tags []}
          translations-map {"my-post" [post
                                       {:id "my-post" :lang "en" :title "EN" :path "p.en.md" :url "u1" :tags []}]}
          result (generate-files/find-translations post translations-map)]
      (is (= [] result)))))

(deftest test-add-translations-to-posts
  (testing "adds translations to posts with correct structure"
    (let [posts [{:id "p1" :lang nil :title "P1" :path "p1.md" :url "u1" :tags ["a"]}
                 {:id "p1" :lang "en" :title "P1 EN" :path "p1.en.md" :url "u2" :tags ["b"]}]
          translations-map (generate-files/build-translations-map posts)
          result (generate-files/add-translations-to-posts posts translations-map)
          first-translations (:translations (first result))
          second-translations (:translations (second result))]
      ;; Verify count
      (is (= 1 (count first-translations)))
      (is (= 1 (count second-translations)))
      ;; Verify translation structure for first post (default lang -> en translation)
      (let [t (first first-translations)]
        (is (= "en" (:lang t)))
        (is (= "P1 EN" (:title t)))
        (is (= "p1.en.md" (:path t)))
        (is (= "u2" (:url t)))
        (is (= ["b"] (:tags t)))
        (is (= "data.en/index.json" (:data-file t))))
      ;; Verify translation structure for second post (en -> default lang translation)
      (let [t (first second-translations)]
        (is (nil? (:lang t)))
        (is (= "P1" (:title t)))
        (is (= "p1.md" (:path t)))
        (is (= "u1" (:url t)))
        (is (= ["a"] (:tags t)))
        (is (= "data/index.json" (:data-file t)))))))

;; ============================================================================
;; Tests for Data Structure Building
;; ============================================================================

(deftest test-make-meta
  (testing "builds meta info"
    (let [result (generate-files/make-meta 25 3 "2024-01-15T10:00:00+08:00")]
      (is (= 25 (:total-posts result)))
      (is (= 3 (:total-pages result)))
      (is (= 10 (:per-page result)))
      (is (= "2024-01-15T10:00:00+08:00" (:generated-at result))))))

(deftest test-build-page-data
  (testing "builds first page data"
    (let [result (generate-files/build-page-data 1 3 [{:id "p1"}] {:total-posts 25})]
      (is (= {:total-posts 25} (:meta result)))
      (is (= [{:id "p1"}] (:posts result)))
      (is (= 1 (get-in result [:pagination :current-page])))
      (is (false? (get-in result [:pagination :has-prev])))
      (is (true? (get-in result [:pagination :has-next])))
      (is (nil? (get-in result [:pagination :prev-file])))
      (is (= "page-2.json" (get-in result [:pagination :next-file])))))
  (testing "builds middle page data"
    (let [result (generate-files/build-page-data 2 3 [{:id "p1"}] {:total-posts 25})]
      (is (= {:total-posts 25} (:meta result)))
      (is (= [{:id "p1"}] (:posts result)))
      (is (true? (get-in result [:pagination :has-prev])))
      (is (true? (get-in result [:pagination :has-next])))
      (is (= "index.json" (get-in result [:pagination :prev-file])))
      (is (= "page-3.json" (get-in result [:pagination :next-file])))))
  (testing "builds last page data"
    (let [result (generate-files/build-page-data 3 3 [{:id "p1"}] {:total-posts 25})]
      (is (= {:total-posts 25} (:meta result)))
      (is (= [{:id "p1"}] (:posts result)))
      (is (true? (get-in result [:pagination :has-prev])))
      (is (false? (get-in result [:pagination :has-next])))
      (is (= "page-2.json" (get-in result [:pagination :prev-file])))
      (is (nil? (get-in result [:pagination :next-file]))))))

(deftest test-build-tag-data
  (testing "builds tag data with posts passed through unchanged"
    (let [posts [{:id "p1" :title "Post 1"} {:id "p2" :title "Post 2"}]
          meta-info {:total-posts 2}
          result (generate-files/build-tag-data "clojure" posts meta-info)]
      (is (= "clojure" (:tag result)))
      (is (= 2 (:count result)))
      (is (= meta-info (:meta result)))
      ;; Verify posts are passed through unchanged
      (is (= posts (:posts result))))))

(deftest test-build-tags-index-data
  (testing "builds tags index sorted by count with complete structure"
    (let [tags-map {"a" [{:id "p1"} {:id "p2"}] "b" [{:id "p3"}]}
          meta-info {:total-posts 3}
          result (generate-files/build-tags-index-data tags-map meta-info)]
      (is (= meta-info (:meta result)))
      (is (= 2 (count (:tags result))))
      ;; First tag (most posts) - verify all fields
      (let [first-tag (first (:tags result))]
        (is (= "a" (:tag first-tag)))
        (is (= 2 (:count first-tag)))
        (is (= "tags/a.json" (:file first-tag))))
      ;; Second tag - verify all fields
      (let [second-tag (second (:tags result))]
        (is (= "b" (:tag second-tag)))
        (is (= 1 (:count second-tag)))
        (is (= "tags/b.json" (:file second-tag)))))))

(deftest test-build-feed-data
  (testing "builds feed data structure"
    (let [settings {:title "My Blog" :url "https://blog.example.com"}
          result (generate-files/build-feed-data settings [{:id "p1"}] "zh" "2024-01-15T10:00:00+08:00" "meta-url" "feed-id")]
      (is (= "My Blog" (get-in result [:site :title])))
      (is (= "https://blog.example.com" (get-in result [:site :url])))
      (is (= "zh" (get-in result [:site :lang])))
      (is (= "2024-01-15T10:00:00+08:00" (get-in result [:site :updated-at])))
      (is (= "meta-url" (:meta-url result)))
      (is (= "feed-id" (:feed-id result)))
      (is (= [{:id "p1"}] (:posts result))))))

;; ============================================================================
;; Tests for I/O Functions (with mocked dependencies)
;; ============================================================================

(def mock-settings
  {:extname ".md"
   :github-repo "user/repo"
   :url "https://blog.example.com"
   :article-url "#/goto/{mdUrl}"
   :title "Test Blog"
   :description "A test blog"
   :lang "zh"
   :author-name "Test Author"
   :author-email "test@example.com"
   :author-uri "https://example.com"})

(deftest test-get-post-metadata-with-mock
  (testing "builds metadata from mocked file"
    (let [slurped-path (atom nil)]
      (with-redefs [generate-files/read-settings (fn [] mock-settings)
                    generate-files/git-file-dates (fn [_] {:created-at "2024-01-15T10:00:00+08:00"
                                                           :updated-at "2024-01-20T10:00:00+08:00"})
                    slurp (fn [path] (reset! slurped-path (str path))
                            "---\ntitle: Mocked Post\ntags:\n  - test\n---\n\nMocked content")]
        (let [result (generate-files/get-post-metadata "/fake/path/2024-01-15-mocked-post.md")]
          (is (= "/fake/path/2024-01-15-mocked-post.md" @slurped-path))
          (is (= "Mocked Post" (:title result)))
          (is (= ["test"] (:tags result)))
          (is (= "2024-01-15" (:date result)))
          (is (nil? (:raw-content result))))))) ;; raw-content should be removed

  (testing "handles post with language suffix"
    (let [slurped-path (atom nil)]
      (with-redefs [generate-files/read-settings (fn [] mock-settings)
                    generate-files/git-file-dates (fn [_] {})
                    slurp (fn [path] (reset! slurped-path (str path))
                            "---\ntitle: English Post\n---\n\nContent")]
        (let [result (generate-files/get-post-metadata "/fake/2024-01-15-post.en.md")]
          (is (= "/fake/2024-01-15-post.en.md" @slurped-path))
          (is (= "en" (:lang result)))
          (is (= "2024-01-15-post" (:id result))))))))

(deftest test-get-post-with-content-with-mock
  (testing "builds post with HTML content"
    (let [slurped-path (atom nil)]
      (with-redefs [generate-files/read-settings (fn [] mock-settings)
                    generate-files/git-file-dates (fn [_] {:created-at "2024-01-15T10:00:00+08:00"
                                                           :updated-at "2024-01-20T10:00:00+08:00"})
                    slurp (fn [path] (reset! slurped-path (str path))
                            "---\ntitle: Test Post\ntags:\n  - clojure\n---\n\n**Bold** text")
                    generate-files/markdown->html (fn [md] (str "<p>" md "</p>"))]
        (let [result (generate-files/get-post-with-content "/fake/2024-01-15-test.md")]
          (is (= "/fake/2024-01-15-test.md" @slurped-path))
          (is (= "Test Post" (:title result)))
          (is (= "<p>**Bold** text</p>" (:content result)))
          (is (= "https://blog.example.com/#/goto/2024-01-15-test.md" (:post-url result)))
          (is (= "clojure" (:category result)))
          (is (nil? (:raw-content result)))))))

  (testing "processes local article links in content"
    (let [slurped-path (atom nil)]
      (with-redefs [generate-files/read-settings (fn [] mock-settings)
                    generate-files/git-file-dates (fn [_] {})
                    slurp (fn [path] (reset! slurped-path (str path))
                            "---\ntitle: Post with Link\n---\n\nSee [other](./2024-01-10-other.md)")
                    generate-files/markdown->html identity]
        (let [result (generate-files/get-post-with-content "/fake/2024-01-15-test.md")]
          (is (= "/fake/2024-01-15-test.md" @slurped-path))
          (is (= "See [other](https://blog.example.com/#/goto/2024-01-10-other.md)" (:content result))))))))

(deftest test-get-all-posts-with-mock
  (testing "returns sorted posts from mocked files"
    (with-redefs [generate-files/get-post-files (fn [] ["/fake/2024-01-15-post1.md"
                                                        "/fake/2024-01-20-post2.md"])
                  generate-files/get-post-metadata (fn [path]
                                                     (if (str/includes? path "post1")
                                                       {:id "post1" :title "Post 1" :updated-at "2024-01-15T00:00:00+08:00"}
                                                       {:id "post2" :title "Post 2" :updated-at "2024-01-20T00:00:00+08:00"}))]
      (let [result (generate-files/get-all-posts)]
        (is (= 2 (count result)))
        (is (= "post2" (:id (first result)))) ;; newest first
        (is (= "post1" (:id (second result))))))))

(deftest test-get-posts-for-feed-with-mock
  (testing "returns posts with content for feed"
    (with-redefs [generate-files/get-post-files (fn [] ["/fake/2024-01-15-post.md"])
                  generate-files/get-post-with-content (fn [_]
                                                         {:id "post" :title "Feed Post"
                                                          :content "<p>HTML</p>"
                                                          :updated-at "2024-01-15T00:00:00+08:00"})]
      (let [result (generate-files/get-posts-for-feed)]
        (is (= 1 (count result)))
        (is (= "<p>HTML</p>" (:content (first result))))))))

(deftest test-generate-posts-json-for-lang-with-mock
  (testing "generates JSON structure for language"
    (let [written-files (atom {})]
      (with-redefs [generate-files/now-iso (fn [] "2024-01-20T12:00:00+08:00")
                    fs/create-dirs (fn [_] nil)
                    generate-files/write-json-file (fn [path data]
                                                     (swap! written-files assoc (str path) data))]
        (let [posts [{:id "p1" :lang nil :tags ["a"] :updated-at "2024-01-15T00:00:00+08:00"}
                     {:id "p2" :lang nil :tags ["a" "b"] :updated-at "2024-01-16T00:00:00+08:00"}]
              translations-map {}
              result (generate-files/generate-posts-json-for-lang posts translations-map nil)]
          (is (= 1 (:pages result)))
          (is (= 2 (:tags result)))
          (is (nil? (:lang result)))
          ;; Verify index.json structure
          (let [index-entry (first (filter #(str/ends-with? (key %) "index.json") @written-files))
                index-data (val index-entry)]
            (is (some? index-entry) "index.json should be written")
            (is (= 2 (get-in index-data [:meta :total-posts])))
            (is (= 1 (get-in index-data [:meta :total-pages])))
            (is (= "2024-01-20T12:00:00+08:00" (get-in index-data [:meta :generated-at])))
            (is (= 1 (get-in index-data [:pagination :current-page])))
            (is (= 2 (count (:posts index-data))))
            (is (= "p1" (:id (first (:posts index-data))))))
          ;; Verify tags.json structure with actual values
          (let [tags-entry (first (filter #(str/ends-with? (key %) "tags.json") @written-files))
                tags-data (val tags-entry)
                tag-a (first (filter #(= "a" (:tag %)) (:tags tags-data)))
                tag-b (first (filter #(= "b" (:tag %)) (:tags tags-data)))]
            (is (some? tags-entry) "tags.json should be written")
            (is (= 2 (count (:tags tags-data))))
            ;; Verify actual counts, not just field existence
            (is (= 2 (:count tag-a)) "tag 'a' should have count 2")
            (is (= 1 (:count tag-b)) "tag 'b' should have count 1")
            ;; Verify file paths
            (is (= "tags/a.json" (:file tag-a)))
            (is (= "tags/b.json" (:file tag-b))))))))

  (testing "generates posts with translations"
    (let [written-files (atom {})]
      (with-redefs [generate-files/now-iso (fn [] "2024-01-20T12:00:00+08:00")
                    fs/create-dirs (fn [_] nil)
                    generate-files/write-json-file (fn [path data]
                                                     (swap! written-files assoc (str path) data))]
        (let [;; p1 has default lang version, p1.en has English version
              posts [{:id "p1" :lang nil :title "P1" :path "p1.md" :url "u1" :tags ["a"] :updated-at "2024-01-15T00:00:00+08:00"}]
              ;; translations-map includes the English version
              translations-map {"p1" [{:id "p1" :lang nil :title "P1" :path "p1.md" :url "u1" :tags ["a"]}
                                      {:id "p1" :lang "en" :title "P1 EN" :path "p1.en.md" :url "u2" :tags ["b"]}]}
              result (generate-files/generate-posts-json-for-lang posts translations-map nil)]
          (is (= 1 (:pages result)))
          ;; Verify translations in index.json
          (let [index-entry (first (filter #(str/ends-with? (key %) "index.json") @written-files))
                index-data (val index-entry)
                first-post (first (:posts index-data))
                translations (:translations first-post)]
            (is (some? index-entry) "index.json should be written")
            (is (= 1 (count translations)) "should have 1 translation")
            (let [t (first translations)]
              (is (= "en" (:lang t)))
              (is (= "P1 EN" (:title t)))
              (is (= "data.en/index.json" (:data-file t)))))))))

  (testing "generates multiple pages with pagination"
    (let [written-files (atom {})]
      (with-redefs [generate-files/now-iso (fn [] "2024-01-20T12:00:00+08:00")
                    generate-files/posts-per-page 1  ;; Force 1 post per page
                    fs/create-dirs (fn [_] nil)
                    generate-files/write-json-file (fn [path data]
                                                     (swap! written-files assoc (str path) data))]
        (let [posts [{:id "p1" :lang nil :tags ["a"] :updated-at "2024-01-15T00:00:00+08:00"}
                     {:id "p2" :lang nil :tags ["a"] :updated-at "2024-01-16T00:00:00+08:00"}]
              result (generate-files/generate-posts-json-for-lang posts {} nil)]
          (is (= 2 (:pages result)))
          ;; Verify index.json (page 1)
          (let [index-entry (first (filter #(str/ends-with? (key %) "index.json") @written-files))
                index-data (val index-entry)]
            (is (some? index-entry) "index.json should be written")
            (is (= 2 (get-in index-data [:meta :total-posts])))
            (is (= 2 (get-in index-data [:meta :total-pages])))
            (is (= 1 (get-in index-data [:pagination :current-page])))
            (is (true? (get-in index-data [:pagination :has-next])))
            (is (= "page-2.json" (get-in index-data [:pagination :next-file]))))
          ;; Verify page-2.json exists
          (let [page2-entry (first (filter #(str/ends-with? (key %) "page-2.json") @written-files))
                page2-data (val page2-entry)]
            (is (some? page2-entry) "page-2.json should be written")
            (is (= 2 (get-in page2-data [:pagination :current-page])))
            (is (true? (get-in page2-data [:pagination :has-prev])))
            (is (false? (get-in page2-data [:pagination :has-next])))
            (is (= "index.json" (get-in page2-data [:pagination :prev-file])))))))))

(deftest test-generate-feed-for-lang-with-mock
  (testing "generates feed for specific language with correct content"
    (let [written-content (atom nil)
          written-path (atom nil)
          slurped-path (atom nil)
          ;; More complete template to verify multiple fields
          test-template (str "<?xml version=\"1.0\"?>"
                             "<feed xml:lang=\"{{site.lang}}\">"
                             "<title>{{site.title}}</title>"
                             "<updated>{{site.updated-at}}</updated>"
                             "<id>{{feed-id}}</id>"
                             "{% for post in posts %}"
                             "<entry><title>{{post.title}}</title></entry>"
                             "{% endfor %}"
                             "</feed>")]
      (with-redefs [generate-files/read-settings (fn [] mock-settings)
                    generate-files/now-iso (fn [] "2024-01-20T12:00:00+08:00")
                    slurp (fn [path]
                            (reset! slurped-path (str path))
                            (if (str/ends-with? (str path) ".selmer")
                              test-template
                              ""))
                    spit (fn [path content]
                           (reset! written-path (str path))
                           (reset! written-content content))]
        (let [posts [{:id "p1" :lang "en" :title "EN Post" :updated-at "2024-01-15T00:00:00+08:00"}]
              result (generate-files/generate-feed-for-lang posts "en")
              expected (str "<?xml version=\"1.0\"?>"
                            "<feed xml:lang=\"en\">"
                            "<title>Test Blog</title>"
                            "<updated>2024-01-15T00:00:00+08:00</updated>"
                            "<id>https://github.com/user/repo/</id>"
                            "<entry><title>EN Post</title></entry>"
                            "</feed>")]
          (is (= "feed.en.xml" result))
          (is (str/ends-with? @slurped-path "feed.xml.selmer"))
          (is (str/ends-with? @written-path "feed.en.xml"))
          (is (= expected @written-content))))))

  (testing "returns nil when no posts for language"
    (let [posts [{:id "p1" :lang "zh"}]
          result (generate-files/generate-feed-for-lang posts "en")]
      (is (nil? result)))))

;; ============================================================================
;; Tests for Edge Cases
;; ============================================================================

(deftest test-tag-to-filename-edge-cases
  (testing "handles empty string"
    (is (= ".json" (generate-files/tag-to-filename ""))))
  (testing "handles only special characters"
    (is (= ".json" (generate-files/tag-to-filename "+++"))))
  (testing "handles mixed content"
    (is (= "hello-world.json" (generate-files/tag-to-filename "Hello...World!!!")))))

(deftest test-article-url-edge-cases
  (testing "handles template with trailing slash in base URL"
    (is (= "https://example.com/posts/file.md"
           (generate-files/article-url "/posts/{mdUrl}" "https://example.com/" "repo" "file.md" nil))))
  ;; Note: RFC 6570 URI templates expand missing/empty variables to empty string,
  ;; so /{lang}/posts/{mdUrl} with no lang becomes //posts/file.md.
  ;; This is expected behavior - templates with optional path segments should
  ;; use a pattern without the {lang} placeholder when lang is not needed.
  (testing "missing lang in template with lang placeholder produces double slash (RFC 6570 behavior)"
    (is (= "https://example.com//posts/file.md"
           (generate-files/article-url "/{lang}/posts/{mdUrl}" "https://example.com" "repo" "file.md" nil)))))

(deftest test-build-post-metadata-edge-cases
  (testing "handles post without date in filename"
    (let [settings {:extname ".md" :github-repo "user/repo"}
          result (generate-files/build-post-metadata settings "readme.md" "content" {})]
      (is (nil? (:date result)))
      (is (= "readme" (:id result)))))
  (testing "handles frontmatter with extra fields"
    (let [settings {:extname ".md" :github-repo "user/repo"}
          content "---\ntitle: Test\ncustom: value\n---\nBody"
          result (generate-files/build-post-metadata settings "2024-01-01-test.md" content {})]
      (is (= "Test" (:title result))))))

(deftest test-replace-local-article-links-edge-cases
  (testing "handles multiple links in same content"
    (let [md "[A](./a.md) and [B](./b.md)"
          result (generate-files/replace-local-article-links "#/{mdUrl}" "https://x.com" "r" ".md" md)]
      (is (= "[A](https://x.com/#/a.md) and [B](https://x.com/#/b.md)" result))))
  (testing "handles link with spaces in text"
    (let [md "[Link Text Here](./post.md)"
          result (generate-files/replace-local-article-links "#/{mdUrl}" "https://x.com" "r" ".md" md)]
      (is (= "[Link Text Here](https://x.com/#/post.md)" result))))
  (testing "preserves non-md links"
    (let [md "[Image](./image.png)"
          result (generate-files/replace-local-article-links "#/{mdUrl}" "https://x.com" "r" ".md" md)]
      (is (= md result)))))

;; ============================================================================
;; Run Tests
;; ============================================================================

(defn -main []
  (let [result (run-tests 'generate-files-test)]
    (System/exit (if (and (zero? (:fail result)) (zero? (:error result))) 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
