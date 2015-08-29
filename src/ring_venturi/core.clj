(ns ring-venturi.core)

(defprotocol RateLimiter
  (try-make-request [this client-id]))

(def backoff-response {:status 429})

(defn limit [handler limiter id-fn]
  (fn [request]
    (if-let [id (id-fn request)]
      (if (.try-make-request limiter id)
        (handler request)
        backoff-response)
      (handler request))))
