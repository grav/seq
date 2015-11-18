(ns seq.midi
  (:require [seq.util :as u]))

(defn inputs [ma]
  (->> (.-inputs ma)
      (u/js-iterable->vec)
      (map second)))

(defn outputs [ma]
  (->> (.-inputs ma)
       (u/js-iterable->vec)
       (map second)))

(defn def-ma []
  (-> (js/navigator.requestMIDIAccess)
      (.then (fn [m] (def ma m)))))

