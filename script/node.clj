(require 'cljs.build.api)

(cljs.build.api/build "src-nodejs"
                      {:main 'seq.nodejs
                       :output-to "main.js"
                       :target :nodejs})
