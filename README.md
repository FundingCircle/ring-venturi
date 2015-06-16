# Ring-venturi

A Clojure library for rate limiting ring applications.

## Installation

[ring-venturi "0.2.0"]

_WARNING: Alpha software, breaking changes ahead!_

## Usage

### Frequency based limiting

Only in-memory atom storage is implemented. This will only allow
requests through at a fixed interval.

```clojure
(ns my-app.core
  (:require [boot.core :as b]
            [ring.adapter.jetty :as jetty]
            [ring-venturi.frequency :as frequency]))

(defn handler [r]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "I'm in!"})

(def cache (frequency/in-memory-limiter 1000)) ;; 1 request per second!

(def app (-> handler
             (frequency/limit cache :query-string)))

(b/deftask server []
  (jetty/run-jetty app {:port 3000}))
```

### Period based limiting


```clojure
(ns myapp.core
  (:require [clojurewerkz.spyglass.client :refer [text-connection]]
            [ring-venturi.core :as rate-limit]
            [ring-venturi.cache.memcached :refer [new-cache]])

(def memcached (text-connection "localhost:11211"))
(def rate-limit-cache (new-cache memcached))

(def app (-> root-handler
             (rate-limit/limit-requests-per-hour rate-limit-cache {:limit 100
                                                                   :identifier-fn (fn [request] (:id request)})))
```

The rate limit middleware is responsible for limiting the number of
requests a user can make in a time period.  The rate limit function
requires a cache to store request counts and an identifier function to
map the request to a user.

### Supported Limits

* `limit-requests-per-hour`
* `limit-requests-per-minute`
* `limit-requests-per-second`

## License

Copyright © 2015 Funding Circle

Distributed under the BSD 3-Clause License.
