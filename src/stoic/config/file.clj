(ns stoic.config.file
  "Loads stoic config from a file containing edn.The file is watched for changes and config is reloaded when they occur."
  (:require [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [com.stuartsierra.component :refer [Lifecycle]]
            [stoic.protocols.config-supplier :refer [ConfigSupplier]]
            [juxt.dirwatch :refer [watch-dir close-watcher]])
  (:import (java.nio.file AccessDeniedException)))

(defn- read-config [file-path]
  "Reads the edn based config from the specified file"
  (log/info "Reading config from file: " file-path)
  (let [config (edn/read-string (slurp file-path))]
    (log/debug "Config: " config)
    config))

(defn- config-file-change? [config-path f]
  "Filter out all file system events that do not match the stoic config file modification or creation"
  (and (= config-path (.getAbsolutePath (:file f))) (not= (:action f) :delete)))

(defn- reload-config! [config config-path watch-fns file-events]
  "If the stoic config file has changed, reload the config and call the optional watch function"
  (log/debug "reload-config! called")
  (when (config-file-change? config-path file-events)
    (log/debug "Reloading config...")
    (let [old-config @config
          new-config (read-config config-path)]
      (reset! config new-config)
      (doseq [[path watch-fn] @watch-fns]
        (when-not (= (get-in old-config path) (get-in new-config path))
          (watch-fn))))))

(defrecord FileConfigSupplier [file-path]
  ConfigSupplier
  Lifecycle

  (start [{:keys [config-watcher] :as this}]
    (if-not config-watcher
      (let [config-path (.getAbsolutePath (io/file file-path))
            config-dir (.getParentFile (io/as-file config-path))]
        (try
          (let [config (atom (read-config config-path))
                watch-fns (atom [])
                config-watcher (watch-dir
                                (partial reload-config! config
                                         config-path watch-fns) config-dir)]
            (assoc this :config config
                   :config-watcher config-watcher
                   :watch-fns watch-fns))
          (catch AccessDeniedException e
           (do
             (log/fatal "Unable to assign watcher to directory "
                        (.getAbsolutePath config-dir) " check permissions")
             (throw e)))))
      this))

  (stop [{:keys [config-watcher] :as this}]
    (when config-watcher
      (close-watcher config-watcher))
    (dissoc this :config-watcher :watch-fns))

  (fetch [this path]
    (get-in @(:config this) path))

  (watch! [{:keys [watch-fns]} path watch-fn]
    (swap! watch-fns conj [path watch-fn])))

(defn config-supplier [config]
  (FileConfigSupplier. (:path config)))
