(ns stoic.config.supplier)

(defn- make-file-config-supplier [path]
  (do
    (require 'stoic.config.file)
    ((resolve 'stoic.config.file/config-supplier) path)))

(defn- make-curator-config-supplier [path]
  (do
    (require 'stoic.config.curator)
    ((resolve 'stoic.config.curator/config-supplier) path)))

(defn make-config-supplier [config-supplier-type path]
  (cond
   (= config-supplier-type :file) (make-file-config-supplier path)
   (= config-supplier-type :zk) (make-curator-config-supplier path)
   :default (throw (IllegalArgumentException. "Args should be 'config-supplier-type' and 'path', where 'config-supplier-type' is either :file or :zk, and 'path' is the path to the config-file or zk root, for :file and :zk, respectively."))))
