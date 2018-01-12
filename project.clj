(defproject fundingcircle/ring-venturi "0.2.2-SNAPSHOT"
  :description "Ring rate limiter"
  :url "https://github.com/FundingCircle/ring-venturi"
  :license {:name "BSD 3-clause"
            :url "http://opensource.org/licenses/BSD-3-Clause"}
  :plugins [[speclj "3.2.0"]]
  :profiles {:dev {:dependencies [[speclj "3.3.2"]]}}
  :test-paths ["spec"]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [clj-time "0.14.2"]
                 [org.clojure/core.cache "0.6.5"]
                 [clojurewerkz/spyglass "1.2.0"]
                 [org.clojure/data.json "0.2.6"]]
  :deploy-repositories [["releases" :clojars]])
