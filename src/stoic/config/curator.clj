(ns stoic.config.curator
  (:require [curator.framework :refer (curator-framework)]
            [stoic.protocols.config-supplier :refer [ConfigSupplier]]
            [stoic.config.data :refer [serialize-form deserialize-form path-for]]
            [com.stuartsierra.component :refer [Lifecycle]]
            [clojure.tools.logging :as log])
  (:import [org.apache.curator.framework.api CuratorWatcher CuratorEventType]))

(defn- connect [zk-conn]
  (let [client (curator-framework zk-conn)]
    (.start client)
    client))

(defn close [client]
  (.close client))

(defn add-to-zk [client path m]
  (when-not (.. client checkExists (forPath path))
    (.. client create (forPath path nil)))
  (.. client setData (forPath path (serialize-form m))))

(defn read-from-zk [client path]
  (deserialize-form (.. client getData (forPath path))))

(defn- watch-path [client path watcher]
  (.. client checkExists watched (usingWatcher watcher)
      (forPath path)))

(defrecord CuratorConfigSupplier [zk-conn root]
  ConfigSupplier
  Lifecycle

  (start [{:keys [client] :as this}]
    (if client this (assoc this :client (connect zk-conn))))

  (stop [{:keys [client] :as this}]
    (when client
      (log/info "Disconnecting from ZK")
      (close client))
    this)

  (fetch [{:keys [client]} k]
    (let [path (path-for root k)]
      (when-not (.. client checkExists (forPath path))
        (.. client create (forPath path nil)))
      (read-from-zk client path)))

  (watch! [{:keys [client root] :as this} p watcher-fn]
    (let [path (path-for root p)]
      (watch-path client path
                  (reify CuratorWatcher
                    (process [this event]
                      (when (= :NodeDataChanged (keyword (.. event getType name)))
                        (log/info "Data changed, firing watcher" event)
                        (watcher-fn)
                        (watch-path client path this))))))))

(defn config-supplier [config]
  (CuratorConfigSupplier. (:zk-conn config) (:path config)))
