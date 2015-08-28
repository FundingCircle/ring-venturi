(ns ring-venturi.period-spec
  (:require [ring-venturi.period :as period]
            [speclj.core :refer :all]
            speclj.run.standard))

(describe "in-memory-limiter"
  (with handler (constantly {:status 200}))
  (with limiter (period/in-memory-limiter 1000))
  (with app (period/limit @handler @limiter :id ))
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
        (should= 200 (:status (@app @request)))))

  (describe "when request has no user"
    (before (@app {}))
    (it "gets through"
        (should= 200 (:status (@app {}))))))

(run-specs)
