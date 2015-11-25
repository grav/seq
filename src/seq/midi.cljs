(ns seq.midi
  (:require [seq.util :as u]))



(defn inputs [ma]
  (->> (.-inputs ma)
      (u/js-iterable->vec)
      (map second)))

(defn outputs [ma]
  (->> (.-outputs ma)
       (u/js-iterable->vec)
       (map second)))

(defn get-output [outputs id]
  (-> (filter #(= (.-id %) id) outputs)
      first))

(defn def-ma []
  (-> (js/navigator.requestMIDIAccess)
      (.then (fn [m] (def ma m)))))

