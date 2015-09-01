# Ring-venturi

A Clojure library for rate limiting ring applications.

## Installation

[![Clojars Project](http://clojars.org/ring-venturi/latest-version.svg)](http://clojars.org/ring-venturi)

_WARNING: Alpha software, breaking changes ahead!_

## Usage

```clojure
(ns my-app.core
  (:require [boot.core :as b]
            [ring.adapter.jetty :as jetty]
            [ring-venturi.core :as core]
            [ring-venturi.period :as period]))

(defn handler [r]
  {:status 200
   :headers {"Content-Type" "text/plain"}
   :body "I'm in!"})

(def app (-> handler
             (core/limit (period/every-millis 25 (period/memory-backend))
                         :query-string)))

(b/deftask server []
  (jetty/run-jetty app {:port 3000}))
```

### Frequency based limiting

Frequency limiters are used for limiting to x requests per [second/minute/hour].

The following time units are supported:

* Hours: `(f/per-hour 10 (f/memcached-backend))`
* Minutes: `(f/per-minute 10 (f/memcached-backend))`
* Seconds: `(f/per-second 10 (f/memcached-backend))`

Supported backends include:

* Memcached: `(f/memcached-backend memcached-client)`

### Period based limiting

Period limiters are used for only allowing a request every x milliseconds.

Supported backend include:

* In memory: `(p/memory-backend)`
* Memcached: `(p/memcached-backend memcached-client)`
* Redis: `(p/redis-backend redis-connection-opts)`

## License

Copyright © 2015 Funding Circle

Distributed under the BSD 3-Clause License.
