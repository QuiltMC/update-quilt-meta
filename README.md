# Quilt Meta

Quilt Meta ia a json HTTP api that can be used to query meta data about Quilt projects.

It can be used by tools or launchers that wish to query version information about Quilt.

Hosted at [https://meta.quiltmc.org/](https://meta.quiltmc.org/)

# What is this repository?

This repository contains a [GitHub Action custom action](https://docs.github.com/en/actions/creating-actions/about-custom-actions)
that will update the Quilt Meta content.

# Documentation

Please see the [Meta API index](https://meta.quiltmc.org/) for documentation.

The versions are in order, the newest versions appear first

`game_version` and `loader_version` should be url encoded to allow for special characters. For example `1.14 Pre-Release 5` becomes `1.14%20Pre-Release%205`
