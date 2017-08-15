(ns  ^:figwheel-always seq.nodejs
  (:require [cljs.nodejs :as nodejs]
            [seq.core :as seq]))

(nodejs/enable-util-print!)

(def midi-access (nodejs/require "webmidi-shim"))

(defonce app-state (atom nil))

(defn -main [& args]
  (let [nav (nodejs/require "webmidi-shim")
        now (nodejs/require "performance-now")]
    (seq/setup-midi! app-state nav.requestMIDIAccess now)
    (seq/play-sequences! app-state now 0 0))


  #_(-> (midi-access.requestMIDIAccess)
      (.then (fn [ma]
               (-> (ma.outputs.values)
                   es6-iterator-seq
                   first
                   (.send #js [0x90 48 64] 1000))))))





(set! *main-cli-fn* -main)
