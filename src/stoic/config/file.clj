(ns stoic.config.file
  "Loads stoic config from a file containing edn.The file is watched for changes and config is reloaded when they occur."
  (:import (java.nio.file AccessDeniedException))
  (:require [stoic.protocols.config-supplier]
            [clojure.tools.logging :as log]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [environ.core :as environ]
            [com.stuartsierra.component :as component]
            [juxt.dirwatch :refer (watch-dir close-watcher)]))

(defn read-config-path []
  (let [path (environ/env :am-config-path)]
    (when (string/blank? path)
      (throw (IllegalArgumentException.
               "Please set AM_CONFIG_PATH environment variable to the absolute path of your application config file")))
    path))

(def ^:dynamic *read-config-path* read-config-path)

(defn enabled? []
  (try
    (*read-config-path*)
    true
    (catch IllegalArgumentException e false)))

(defn read-config [file-path]
  "Reads the edn based config from the specified file"
  (log/info "Reading config from file: " file-path)
  (let [config (edn/read-string (slurp file-path))]
    (log/debug "Config: " config)
    config))

(defn- config-file-change? [config-path f]
  "Filter out all file system events that do not match the stoic config file modification or creation"
  (and (= config-path (.getAbsolutePath (:file f))) (not= (:action f) :delete)))

(defn reload-config! [config config-path watch-fns file-events]
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
  stoic.protocols.config-supplier/ConfigSupplier
  component/Lifecycle

  (start [{:keys [config-watcher] :as this}]
    (if-not config-watcher
      (let [config-path (.getAbsolutePath (io/file (or file-path (*read-config-path*))))
            config-dir (.getParentFile (io/as-file config-path))]
        (try
          (let [
                config (atom (read-config config-path))
                watch-fns (atom [])
                config-watcher (watch-dir
                               (partial reload-config! config
                                        config-path watch-fns) config-dir)]
            (assoc this :config config
                   :config-watcher config-watcher
                   :watch-fns watch-fns))
          (catch AccessDeniedException e
           (do
             (log/fatal "Unable to assign watcher to directory " (.getAbsolutePath config-dir) " check permissions")
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

(defn config-supplier
  ([]
     (FileConfigSupplier. nil))
  ([file-path]
     (FileConfigSupplier. file-path)))
