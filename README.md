# Ring-venturi

A Clojure library for rate limiting ring applications.

## Installation

[ring-venturi "0.1.0"]

## Usage

```clojure
(ns myapp.core
  (:require [ring-venturi.core :as rate-limit])

(def app (-> root-handler
             (rate-limit/limit-requests-per-hour my-cache {:limit 100
                                                           :identifier-fn (fn [request] (:id request)})))
```

The rate limit middleware is responsible for limiting the number of requests a user can make in a time period.
The rate limit function requires a cache to store request counts and an identifier function to map the request to a
user. 

## License

Copyright © 2015 Funding Circle

Distributed under the BSD 3-Clause License.
