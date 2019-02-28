# Scicloj Tutorials

In this repo is a place for Clojure data science tutorials created by the community.

It is part of Scicloj -- our community effort to create more dialogue and coordination among Clojurians doing data science.

Tutorials will also appear at the Scicloj website.

## Organization of this repo

Soon we will design a more refined directory structure, to reflect different topics, kaggle competetions, etc.

For now, please put your tutorials under the `drafts` subdirectory.

## A tutorial's lifecycle

We distinguish between two kinds of tutorials:

- **experimental** -- These are drafts. They serve a basis for our discussion. They may depend on alpha-state libraries, may have some doubt regarding APIs, may have some incomplete and confusing parts.

- **recommended** -- These are the parts that we consider 'ready'. They can be used do teach newcomers. They use rather stable APIs, and should be clear and tidy.

By default, tutorials are experimental. An tutorial's author can ask to mark it as recommended. To agree on that, at least 2 of the group members will have to read it and feel that is fine.

## Installation

Install [Lein-Jupyter](https://github.com/clojupyter/lein-jupyter) by running `lein jupyter install-kernel` once under this project.

## Usage

Run `jupyter notebook` in a directory containing the project, and just access the tutorials as juypter notebooks.

One may also use  which wraps Clojupyter and adds some tooling, such as [Parinfer](https://shaunlebron.github.io/parinfer/).

## Contact

In addition to the Issues section of this repo, tutorials can be discussed at the [scicloj-tutorials](https://clojurians.zulipchat.com/#narrow/stream/187445-scicloj-tutorials) stream at the Clojurians-Zulip.

