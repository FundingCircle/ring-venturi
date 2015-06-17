(ns ring-venturi.frequency-spec
  (:require [ring-venturi.frequency :as frequency]
            [speclj.core :refer :all]
            speclj.run.standard))


(describe "ring-venturi.frequency"
  (with handler (constantly {:status 200}))
  (with limiter (frequency/in-memory-limiter 1000))
  (with app (frequency/limit @handler @limiter :id ))
  (with request {:id 1})

  (describe "the first request"
    (it "gets through"
        (should= 200 (:status (@app @request)))))

  (describe "a second request within the backoff"
    (before (@app @request))
    (it "is blocked"
        (should= 429 (:status (@app @request)))))

  (describe "a second request after the backoff"
    (before (do
              (@app @request)
              (Thread/sleep 1000)))
    (it "gets through"
        (should= 200 (:status (@app @request))))))

(run-specs)
