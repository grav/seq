(ns seq.nodejs
  (:require [cljs.nodejs :as nodejs]))

(nodejs/enable-util-print!)

(def midi-access (nodejs/require "webmidi-shim"))

(defn -main [& args]
  (-> (midi-access.requestMIDIAccess)
      (.then (fn [ma]
               (-> (ma.outputs.values)
                   es6-iterator-seq
                   first
                   (.send #js [0x90 48 64] 1000))))))





(set! *main-cli-fn* -main)
