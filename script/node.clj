(require 'cljs.build.api)

(cljs.build.api/build "src"
                      {:main 'seq.nodejs
                       :output-to "main.js"
                       :target :nodejs})
