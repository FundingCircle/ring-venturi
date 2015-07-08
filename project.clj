(defproject ring-venturi "0.2.1"
  :description "Ring rate limiter"
  :url "https://github.com/FundingCircle/ring-venturi"
  :license {:name "BSD 3-clause"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :plugins [[speclj "3.2.0"]]
  :profiles {:dev {:dependencies [[speclj "3.2.0"]]}}
  :test-paths ["spec"]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-time "0.9.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [clojurewerkz/spyglass "1.0.0"]
                 [org.clojure/data.json "0.2.6"]])
