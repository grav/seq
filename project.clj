(defproject seq "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.473"]
                 [reagent "0.6.0"]
                 [figwheel-sidecar "0.5.8"]]

  :plugins [[lein-cljsbuild "1.1.5"]
            [lein-figwheel "0.5.8"]]

  :source-paths ["src"]

  :clean-targets ^{:protect false} ["resources/public/js/compiled" "target"]

  :cljsbuild {
              :builds [{:id           "dev"
                        :source-paths ["src" "src-web"]

                        :figwheel     {:on-jsload      "seq.core/on-js-reload"
                                       :websocket-host :js-client-host}

                        :compiler     {:main                 seq.web
                                       :asset-path           "js/compiled/out"
                                       :output-to            "resources/public/js/compiled/seq.js"
                                       :output-dir           "resources/public/js/compiled/out"
                                       :source-map-timestamp true}}
                       {:id           "min"
                        :source-paths ["src" "src-web"]
                        :compiler     {:output-to     "resources/public/js/compiled/seq.js"
                                       :main          seq.web
                                       :optimizations :advanced
                                       :pretty-print  false}}
                       {:id           "nodejs"
                        :figwheel     true
                        :source-paths ["src" "src-nodejs"]
                        :compiler     {:main          seq.nodejs
                                       :target        :nodejs
                                       :output-dir    "target/nodejs"
                                       :output-to     "target/nodejs/main.js"
                                       :source-map    true
                                       :optimizations :none}}]}

  :figwheel {
             ;; :http-server-root "public" ;; default and assumes "resources" 
             ;; :server-port 3449 ;; default
             ;; :server-ip "127.0.0.1" 

             :css-dirs ["resources/public/css"]             ;; watch and update CSS

             ;; Start an nREPL server into the running figwheel process
             ;; :nrepl-port 7888

             ;; Server Ring Handler (optional)
             ;; if you want to embed a ring handler into the figwheel http-kit
             ;; server, this is for simple ring servers, if this
             ;; doesn't work for you just run your own server :)
             ;; :ring-handler hello_world.server/handler

             ;; To be able to open files in your editor from the heads up display
             ;; you will need to put a script on your path.
             ;; that script will have to take a file path and a line number
             ;; ie. in  ~/bin/myfile-opener
             ;; #! /bin/sh
             ;; emacsclient -n +$2 $1
             ;;
             ;; :open-file-command "myfile-opener"

             ;; if you want to disable the REPL
             ;; :repl false

             ;; to configure a different figwheel logfile path
             ;; :server-logfile "tmp/logs/figwheel-logfile.log" 
             })
