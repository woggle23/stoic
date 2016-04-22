(ns stoic.bootstrap
  "Bootstrap a component system with components and settings."
  (:require [clojure.tools.logging :as log]
            [clojure.stacktrace :refer [print-stack-trace]]
            [com.stuartsierra.component :as component]
            [stoic.protocols.config-supplier :as cs]))

(defn- fetch-settings
  "Fetch settings from the config supplier and wrap in atoms."
  [config-supplier system]
  (into {} (for [[k p] system]
             (do
              [k (atom (cs/fetch config-supplier p))]))))

(defn- update-settings [config-supplier settings-atom k p]
  (log/info "Updating settings for: " k)
  (let [settings (cs/fetch config-supplier p)]
    (log/info (format "New settings for %s: %s" k settings))
    (when (not= @settings-atom settings)
      (reset! settings-atom settings))))

(defn- watch-for-changes [config-supplier paths settings]
  (doseq [[k p] paths
          :let [s (k settings)]
          :when s]
    (cs/watch! config-supplier p (partial update-settings config-supplier s k p))))

(defn bootstrap
  "Inject system with settings fetched from a config-supplier.
   Components will be bounced when their respective settings change.
   Returns a SystemMap with Stoic config attached."
  [config-supplier settings-paths]
  (let [config-supplier-component (component/start config-supplier)
        component-settings (fetch-settings config-supplier-component settings-paths)]
    (watch-for-changes config-supplier-component settings-paths component-settings)
    (assoc component-settings :stoic-config config-supplier-component)))

(defn start [system]
  (try
    (component/start system)
    (catch Exception e
      (log/error "Caught exception during startup: " (.getMessage e))
      (when (.getCause e)
        (log/info (format "Original Error: %s\n%s"
                          (-> e .getCause .getMessage)
                          (with-out-str (-> e .getCause print-stack-trace)))))
      (log/info "Shutting down components: ")
     (doseq [[n c] (-> e ex-data :system)]
       (log/info n)
       (component/stop c))
     :start-error)))
