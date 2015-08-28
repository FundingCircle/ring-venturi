(ns ring-venturi.cache
  (:require [ring-venturi.cache :refer :all]
            [speclj.core :refer :all]
            [clojurewerkz.spyglass.client :as c]
            speclj.run.standard))

(def memcached-client (delay (c/text-connection "localhost:11211")))
(def expire-secs 60)

(describe "memcached"
  (with cache (new-memcached-cache @memcached-client))

  (before
    (doseq [key ["key-1" "key-2"]]
      (c/delete @memcached-client key)))

  (describe "inc-request-count"
    (it "sets value when no key exists"
      (should= "1"
               (do
                 (.inc-request-count @cache "key-1" expire-secs)
                 (c/get @memcached-client "key-1"))))

    (it "increments the value when the key exists"
      (should= "5"
               (do
                 (c/set @memcached-client "key-1" expire-secs "4")
                 (.inc-request-count @cache "key-1" expire-secs)
                 (c/get @memcached-client "key-1")))))

  (describe "get-request-counts"
    (it "returns known values"
      (should= [4 5]
               (do
                 (c/set @memcached-client "key-1" expire-secs "4")
                 (c/set @memcached-client "key-2" expire-secs "5")
                 (.get-request-counts @cache ["key-1" "key-2"]))))

    (it "ignores unknown keys"
      (should= []
               (.get-request-counts @cache ["key-1"])))))
