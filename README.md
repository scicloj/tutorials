# Scicloj Tutorials

In this repo is a place for Clojure data science tutorials created by the community.

It is part of Scicloj -- our community effort to create more dialogue and coordination among Clojurians doing data science.

Tutorials will also appear at the Scicloj website.

## Guidelines and organization of this repo

Soon we will design a more refined directory structure, to reflect different topics, kaggle competetions, etc.

For now, please put your tutorials under the **`drafts`** subdirectory.

Please mention your (nick)name(s) at the beginning of any repo you write.

## A tutorial's lifecycle

We distinguish between two kinds of tutorials:

- **experimental** -- These are drafts. They serve a basis for our discussion. They may depend on alpha-state libraries, may have some doubt regarding APIs, may have some incomplete and confusing parts.

- **recommended** -- These are the parts that we consider 'ready'. They can be used do teach newcomers. They use rather stable APIs, and should be clear and tidy.

By default, tutorials are experimental. An tutorial's author can ask to mark it as recommended. To agree on that, at least 2 of the group members will have to read it and feel that is fine.

## Contributing

You are invited to contribute. Please contact us if you wish to.

For the tutorials you write, you can use plain Clojure code, Org-mode, etc. Jupyter will be supported soon, when its [upcoming version](https://github.com/clojupyter/clojupyter/pull/79) is available.

We will soon add guidelines and examples for the various formats.

For external dependencies, you can use [alembic](https://github.com/pallet/alembic). See the [example](./src/drafts/clj_example.clj).

Everything here is under one Leiningen project. This should be fine for most use cases. If, for some reason, you need to create a separate project (e.g., using a different Clojure version), then please do so under a subdirectory.

## Contact

In addition to the Issues section of this repo, the task group working on these tutorials has a private stream at the [clojurians-zulipchat](https://clojurians.zulipchat.com/). To join that stream, please contact Daniel Slutsky there.

