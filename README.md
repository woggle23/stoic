# Stoic

This library is based on [stoic](http://github.com/juxt/stoic). It differs in that it doesn't inject configuration into components of a `system-map`, or "bounce" components on configuration change as the original intended. Instead it will retrieve config for system initialisation, and `watch` for any updates, which will be updated in (and availabe from) the `config-supplier` at runtime.

This readme assumes the reader is familiar with [Stuart Sierra's Componets](https://github.com/stuartsierra/component)

The library will pull configuration from a file or Zookeeper source, and can be used as follows:

## Create a config supplier

The project contains both File and Zookeeper (Curator) config suppliers:

 * `stoic.config.file/config-supplier`
 * `stoic.config.curator/config-supplier`

Provide the `path` as an argument to the above; file-location in the case of `stoic.config.file/config-supplier`, root `znode` in the case of `stoic.config.curator/config-supplier`, to create the desired config-supplier component.

Alternatively, use `stoic.config.supplier/make-config-supplier`. The function takes 2 args:

 * `config-supplier-type`, which must be either `:file` or `:zk`
 * `path` - path to file, or zk root, depending on the value of `config-supplier-type`

## Retrieve config

Calling `stoic.bootstrap/bootstrap` with `config-supplier` and `config-paths` (see example below) args will cause Stoic to:

 * `start` the `config-supplier` component
 * Read in config from the defined `config-paths`
 * Create `watchers` for each config item, updating the values to reflect any changes at source.
 * Return the config in a map, the keys of which are those provided in the `config-paths`, along with the config-supplier, named `stoic-config`.

e.g. Consider the following config, stored in a `edn` config file:

```clojure
 {:components {:db {:url "thin:@localhost:1521/orcl"
                   :user "user-name"
                   :password "password"}
               :rabbit {:host "localhost" :consumer-id "my-consumer"}}
  :applications {:comments-service: "http://comments-service.co.uk/retrieve-comments"}}
```

The `config-path` for a component should be listed in a vector, the above `db` and `rabbit` entries could be accessed as follows:


```clojure
(def ^:private config-paths {:db [:components :db]
	                         :rabbit [:components :rabbit]
	                         :comments-service [:applications :comments-service})
```


Note, the same `config-paths` map would be used to access config from Zookeeper, stored under:

```
<zk-root>/components/db
<zk-root>/components/rabbit
<zk-root>/applications/comments-service
```

The returned map would contain:

```clojure
{:db #<Atom@3b7b4bd: {:url "thin:@localhost:1521/orcl",
	                  :user "user-name", :password "password"}
 :rabbit #<Atom@3b7c840: {:host "localhost"
                          :consumer-id "my-consumer"}
 :comments-service #<Atom@3b7b4f0: {:comments-service: "http://comments-service.co.uk/retrieve-comments"}
 :stoic-config #stoic.config.file.FileConfigSupplier {...}}
```

## Create System Map

Create a Stuart Sierra Component `system-map` as normal, using the returned config. Note, the `stoic-config` should be included in the `system-map` to maintain  correct Component `Lifecycle`.

## Start the System Map

Call `stoic.bootstrap/start`, providing the `system-map` as an argument to start the components.

If an error occurs whilst starting the `system-map`, `stoic` will attempt to `stop` all components, and return `:start-error`.

Any updates made to the configuration will be available from the `stoic-config` component. For example, assuming the `system-map` is called system, and the config supplier was added to the system under `stoic-config`:

`(-> system :stoic-config :config)`


# Full Example

```clojure
(ns my-app.bootstrap
  (:require [com.stuartsierra.component :as component]
            [stoic.bootstrap :as bs]
            [stoic.config.file :as fc]
			[stoic.config.supplier :as config-supplier]
            [my-app.components.web-server :refer [make-webserver]]
            [my-app.endpoints.routes :refer [main-routes]]
            [cljrc-web.components.db :as [make-db-client]]
            [cljrc-web.components.rabbit :refer [make-rabbit-consumer]]))

(def system nil)

(def ^:private config-paths {:db [:components :db]
	                         :rabbit [:components :rabbit}
							 :comments-service [:applications :comments-service}})

(defn- new-system [config]
  (component/system-map
   :db (make-db-client @(:db config))
   :rabbit (make-rabbit-consumer @(:rabbit config))
   :web-server (component/using (make-webserver (main-routes)) [:db :rabbit])
   :config (:stoic-config config)))

(defn init [path]
  (alter-var-root #'system (constantly
                          (bs/bootstrap (fc/config-supplier path)
                                        config-paths)))
  ;; alternatively
  ;; (alter-var-root #'system (constantly
  ;; 	                        (bs/bootstrap (config-supplier/make-config-supplier :file path)
  ;;                                               config-paths)))

(defn start [opts]
  (alter-var-root #'system (fn [s] (new-system s opts)))
  (alter-var-root #'system bs/start))

(defn stop []
  (alter-var-root #'system (fn [s] (when s (component/stop s)))))

(defn go []
	(init (str (System/getenv "HOME") "/.config.edn"))
	(start))

(defn reset []
	(stop)
	;; normally refresh would be called here
	(go))
```
