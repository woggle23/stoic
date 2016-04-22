(ns stoic.test.config.file
  (:require [stoic.config.file :as sf]
            [clojure.java.io :as io]
            [clojure.test :refer :all]
            [com.stuartsierra.component :as component]))

(def input-file-path "./test-resources/config/test.edn")
(def test-file-path "/var/tmp/stoic/test.edn")
(def config-item-path [:server :port])
(def initial-port 8080)
(def new-port 9090)

(defn fixture [f]
  (io/make-parents test-file-path)
  (io/copy (io/file input-file-path) (io/file test-file-path))
  (f)
  (io/delete-file test-file-path))

(deftest read-file
  (is (not= (#'sf/read-config input-file-path) nil)))

(deftest update-config-fire-event
  (let [config (atom (#'sf/read-config test-file-path))
        fn-hit (atom false)]
    (is (= (get-in @config config-item-path) initial-port))
    (spit test-file-path (assoc-in @config config-item-path new-port))
    (#'sf/reload-config! config test-file-path
                         (atom [[config-item-path (fn []
                                                    (prn "Updated handler called")
                                                    (reset! fn-hit true))]])
                         {:file (io/file test-file-path) :count 1, :action :modify})
    (is @fn-hit)
    (is (= (get-in (#'sf/read-config test-file-path) config-item-path) new-port))))

(deftest update-config-no-fire
  (let [config (atom (#'sf/read-config test-file-path))
        fn-hit (atom false)]
    (is (= (get-in @config config-item-path) initial-port))
    (spit test-file-path (assoc @config :sauron "updated"))
    (#'sf/reload-config! config test-file-path
                         (atom [[[:http-kit] (fn []
                                               (prn "Updated handler called")
                                               (reset! fn-hit true))]])
                         {:file (io/file test-file-path) :count 1, :action :modify})
    (is (not @fn-hit))
    (is (= (get-in (#'sf/read-config test-file-path) config-item-path) initial-port))))

(use-fixtures :each fixture)
