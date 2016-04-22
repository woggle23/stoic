(ns stoic.test.bootstrap
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [com.stuartsierra.component :refer [Lifecycle system-map using]]
            [stoic.protocols.config-supplier :refer [ConfigSupplier]]
            [stoic.bootstrap :as bs]
            [stoic.config.file :as sf]))

(def input-file-path "./test-resources/config/test.edn")
(def test-file-path "/var/tmp/stoic/test.edn")
(def config-item-path [:urls :service-a-url])
(def new-port "http://new.com")

(defrecord TestComponent [port threads]
  Lifecycle
  (start [this] this)
  (stop [this] this))

(defrecord TestBarfingComponent []
  Lifecycle
  (start [this]
    (throw (IllegalArgumentException. "Component blew up")))
  (stop [this] this))

(defrecord TestFileConfigSupplier [file-path]
  ConfigSupplier
  Lifecycle

  (start [{:keys [config-watcher] :as this}]
    (if-not config-watcher
      (let [config-path (.getAbsolutePath (io/file file-path))
            config-dir (.getParentFile (io/as-file config-path))]
        (let [config (atom (#'sf/read-config config-path))
              watch-fns (atom [])
              config-watcher (partial #'sf/reload-config! config config-path watch-fns)]
          (assoc this :config config :config-watcher config-watcher :watch-fns watch-fns)))
      this))

  (stop [{:keys [config-watcher] :as this}]
    (dissoc this :config :config-watcher :watch-fns))

  (fetch [this path]
    (get-in @(:config this) path))

  (watch! [{:keys [watch-fns]} path watch-fn]
    (swap! watch-fns conj [path watch-fn])))

(defn fixture [f]
  (io/make-parents test-file-path)
  (io/copy (io/file input-file-path) (io/file test-file-path))
  (f)
  (io/delete-file test-file-path))

(deftest component-created-with-config
  (let [config (bs/bootstrap (->TestFileConfigSupplier test-file-path) {:server [:server]})
        system (system-map :server (map->TestComponent @(:server config)))
        started-system (bs/start system)]
    (is (= (-> system :server :port) 8080))
    (is (= (-> system :server :threads) 5))))

(deftest config-updated
  (let [config (bs/bootstrap (->TestFileConfigSupplier test-file-path) {:urls [:urls]})
        system (system-map :urls (:urls config))
        started-system (bs/start system)]
    (spit test-file-path (assoc-in (-> config :stoic-config :config deref)
                                   config-item-path new-port))
    ((-> config :stoic-config :config-watcher)
     {:file (io/file test-file-path) :count 1, :action :modify})
    (is (= (-> system :urls deref :service-a-url) new-port))))

(deftest component-shutdown-safely
  (let [system1 (bs/start
                 (system-map :test1  (map->TestComponent {:port 8080 :threads 5})))
        system2 (bs/start
                (system-map :test1  (map->TestComponent {:port 8080 :threads 5})
                            :test2 (using
                                    (map->TestBarfingComponent {})
                                    [:test1])))]
    (is (= (-> system1 :test1 :port) 8080))
    (is (= system2 :start-error))))

(use-fixtures :each fixture)
