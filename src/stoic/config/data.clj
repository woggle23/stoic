(ns stoic.config.data)

(def charset "UTF-8")

(defn serialize-form
  "Serializes a Clojure form to a byte-array."
  ([form]
     (.getBytes (pr-str form) ^String charset)))

(defn deserialize-form
  "Deserializes a byte-array to a Clojure form."
  ([form]
     (when form (read-string (String. form ^String charset)))))

(defn path-for [root k]
  (format "%s/%s" root (apply str (interpose "/" (map name k)))))
