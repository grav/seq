(ns nodejs.test
  (:require [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(defn -main [& args]
  (println "Hello world!"))

(def midi-access (nodejs/require "webmidi-shim"))

(-> (midi-access.requestMIDIAccess)
    (.then #(prn %)))

(set! *main-cli-fn* -main)
