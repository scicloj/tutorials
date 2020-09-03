# Scicloj Tutorials

In this repo is a place for Clojure data science tutorials created by the community.

It is part of [Scicloj](https://twitter.com/scicloj) -- our community effort to create more dialogue and coordination among Clojurians creating open source solutions for data science.

Tutorials will also appear at the Scicloj website.

## Contributing

You are invited to contribute. Please contact us if you wish to.

For the tutorials you write, you can use Jupyter, plain Clojure code, Org-mode, etc. 

## Guidelines and organization of this repo

Soon we will design a more refined directory structure, to reflect different topics, kaggle competetions, etc.

For now, please put your tutorials under the **`drafts`** subdirectory.

Please **do not put data files** in git. It is better to add a function or script for bringing them locally.

Please mention your (nick)name(s) at the beginning of any repo you write.

## A tutorial's lifecycle

We distinguish between two kinds of tutorials:

- **experimental** -- These are drafts. They serve a basis for our discussion. They may depend on alpha-state libraries, may have some doubt regarding APIs, may have some incomplete and confusing parts.

- **recommended** -- These are the parts that we consider 'ready'. They can be used do teach newcomers. They use rather stable APIs, and should be clear and tidy.

By default, tutorials are experimental. An tutorial's author can ask to mark it as recommended. To agree on that, at least 2 of the group members will have to read it and feel that is fine.

## Usage 

### Jupyter

- On your first use, install the Clojure jupyter kernel of [clojupyter](https://github.com/clojupyter/clojupyter) as instructed [there](https://github.com/clojupyter/clojupyter#installation). 
- Run Jupyter by `jupyter notebook`.
- Open notebooks from the Jupyter UI at the browser.
- For external dependencies, use clojupyter's tooling. See the [example](./src/drafts/clojupyter_example.ipynb).

### Lein Jupyter
TBD

### Plain Clojure

- On your first use, install Leiningen if you don't have it.
- Run Leiningen's REPL by `lein repl` at the project directory.
- Connect to the REPL from your favorite editor.
- For external dependencies, use [alembic](https://github.com/pallet/alembic). See the [example](./src/drafts/clj_example.clj).

Everything here is under one Leiningen project. This should be fine for most use cases. If, for some reason, you need to create a separate project (e.g., using a different Clojure version), then please do so under a subdirectory.

### Org-mode
TBD

## Contact

The proper place to discuss this is the [#data-science stream](https://clojurians.zulipchat.com/#narrow/stream/151924-data-science) at the [clojurians-zulipchat](https://clojurians.zulipchat.com/).

You can find past discussions at the [#scicloj-tutorials stream](https://clojurians.zulipchat.com/#narrow/stream/187445-scicloj-tutorials).

Here is [some general info about our the Scicloj chat streams](https://scicloj.github.io/pages/chat_streams/).

## License

Copyright © 2019 Scicloj

Distributed under the Eclipse Public License.
