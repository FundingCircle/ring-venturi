(ns ring-venturi.core-spec
  (:require [ring-venturi.core :as core]
            [speclj.core :refer :all]
            speclj.run.standard))

(defrecord ConstantLimiter [atom-store accept-request?]
  core/RateLimiter

  (try-make-request [this client-id]
    (swap! atom-store conj client-id)
    accept-request?))

(def success-response {:status :success})

(describe "limit"
  (with atom-store (atom []))

  (context "when request is accepted"
    (with limit-fn (core/limit (constantly success-response)
                               (ConstantLimiter. @atom-store true)
                               :id))

    (it "returns next handler response"
      (should= success-response
               (@limit-fn {:id 1})))

    (it "identifies the request"
      (should= [1]
               (do
                 (@limit-fn {:id 1})
                 (deref (deref atom-store))))))

  (context "when request is rejected"
    (with limit-fn (core/limit (constantly success-response)
                               (ConstantLimiter. @atom-store false)
                               :id))

    (it "returns backoff response"
      (should= {:status 429}
               (@limit-fn {:id 1}))))

  (context "when there is no request id"
    (with limit-fn (core/limit (constantly success-response)
                               (ConstantLimiter. @atom-store true)
                               :id))

    (it "returns next handler response"
      (should= success-response
               (@limit-fn {})))))

(run-specs)
