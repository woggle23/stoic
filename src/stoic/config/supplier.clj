(ns stoic.config.supplier)

(defn- make-file-config-supplier [config]
  (do
    (require 'stoic.config.file)
    ((resolve 'stoic.config.file/config-supplier) config)))

(defn- make-curator-config-supplier [config]
  (do
    (require 'stoic.config.curator)
    ((resolve 'stoic.config.curator/config-supplier) config)))

(defn make-config-supplier [{:keys [type] :as config}]
  (cond
   (= type :file) (make-file-config-supplier config)
   (= type :zk) (make-curator-config-supplier config)
   :default (throw (IllegalArgumentException. "Args should be a map containing :type and :path keys, where :type is either :file or :zk, and :path is the path to the config-file or zk root, for :file and :zk, respectively. In the case of type :zk, :zk-conn should also be defined."))))
