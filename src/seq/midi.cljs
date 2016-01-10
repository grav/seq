(ns seq.midi
  (:require [seq.util :as u]))



(defn get-output [outputs id]
  (-> (filter #(= (.-id %) id) outputs)
      first))

(defn def-ma []
  (-> (js/navigator.requestMIDIAccess)
      (.then (fn [m] (def ma m)))))


(def all-notes-off [176 123 00])

(def note-on 144)
