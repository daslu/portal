(ns examples.scittle
  (:require [hiccup.core :as hiccup]
            [hiccup.page]
            [portal.api :as portal]
            [clojure.string :as string]
            [clojure.java.browse :as browse]))

(defonce portal-dev
  (portal/url
   (portal/open)))

(def portal-url (let [[host query] (string/split portal-dev #"\?")]
                  (str host "/main.js?" query)))

(defn scittle-script [& cljs-forms]
  [:script {:type "application/x-scittle"}
   (->> cljs-forms
        (map pr-str)
        (string/join "\n"))])

(defn div-and-script [id widget]
  [[:div {:id id}]
   (scittle-script
    (list 'dom/render (list 'fn [] widget)
          (list '.getElementById 'js/document id)))])

(defn pr-str-with-meta [value]
  (binding [*print-meta* true]
    (pr-str value)))

(defn portal-widget [value]
  ['(fn [{:keys [edn-str]}]
      (let [api (js/portal.extensions.vs_code_notebook.activate)]
        [:div
         [:div
          {:ref (fn [el]
                  (.renderOutputItem api
                                     (clj->js {:mime "x-application/edn"
                                               :text (fn [] edn-str)})
                                     el))}]]))
   {:edn-str (pr-str-with-meta value)}])

(defn page [widgets]
  (hiccup.page/html5
   [:head]
   (into
    [:body
     (hiccup.page/include-js "https://unpkg.com/react@18/umd/react.production.min.js"
                             "https://unpkg.com/react-dom@18/umd/react-dom.production.min.js"
                             "https://scicloj.github.io/scittle/js/scittle.js"
                             "https://scicloj.github.io/scittle/js/scittle.reagent.js"
                             portal-url)
     (scittle-script '(ns main
                        (:require [reagent.core :as r]
                                  [reagent.dom :as dom])))]
    (->> widgets
         (map-indexed (fn [i widget]
                        (div-and-script (str "widget" i)
                                        widget)))
         (apply concat)))))

(defn as-portal-hiccup [hiccup]
  (with-meta
    hiccup
    {:portal.viewer/default :portal.viewer/hiccup}))

(defn img [url]
  (as-portal-hiccup
   [:img {:height 50 :width 50
          :src url}]))

(defn md [text]
  (as-portal-hiccup
   [:portal.viewer/markdown
    text]))

(defn vega-lite-point-plot [data]
  (as-portal-hiccup
   [:portal.viewer/vega-lite
    (-> {:data {:values data},
         :mark "point"
         :encoding
         {:size {:field "w" :type "quantitative"}
          :x {:field "x", :type "quantitative"},
          :y {:field "y", :type "quantitative"},
          :fill {:field "z", :type "nominal"}}})]))

(defn random-data [n]
  (->> (repeatedly n #(- (rand) 0.5))
       (reductions +)
       (map-indexed (fn [x y]
                      {:w (rand-int 9)
                       :z (rand-int 9)
                       :x x
                       :y y}))))

(defn random-vega-lite-plot [n]
  (-> n
      random-data
      vega-lite-point-plot))


(def example-values
  [(md "# embed portal in a scittle doc")
   (md "## plain data")
   {:x [1 3 4]}
   (md "## a vector of hiccups containing images")
   [(img "https://clojure.org/images/clojure-logo-120b.png")
    (img "https://raw.githubusercontent.com/djblue/portal/fbc54632adc06c6e94a3d059c858419f0063d1cf/resources/splash.svg")]
   (md "## a vega-lite plot")
   (random-vega-lite-plot 9)])

;; run
(->> example-values
     (map portal-widget)
     page
     (spit "example.html"))

(browse/browse-url "example.html")
