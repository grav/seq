(ns nodejs.nodejs
  (:require [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(defn -main [& args]
  (println "Hello world!"))

(def midi-access (nodejs/require "webmidi-shim"))

(-> (midi-access.requestMIDIAccess)
    (.then (fn [ma] (-> (ma.outputs.values)
                        (.next.value)
                        (.send [0x90 48 64])))))

(set! *main-cli-fn* -main)
