# Quilt Meta

[Quilt Meta](https://meta.quiltmc.org/) ia a json HTTP api that can be used to query meta data about Quilt projects.

It can be used by tools or launchers that wish to query version information about Quilt.

# What is this repository?

This repository contains a [GitHub Action custom action](https://docs.github.com/en/actions/creating-actions/about-custom-actions)
that will update the Quilt Meta content.

# Documentation

Please see the [Meta API index](https://meta.quiltmc.org/) for documentation.

# Using the action

If you are a Quilt project maintainer, you can use this action to update the Quilt Meta content inside your CI. To do so, add the following step to your workflow:

```yaml
- name: Update Quilt Meta
  uses: quiltmc/update-quilt-meta@main
  with:
    b2-key-id: ${{ secrets.META_B2_KEY_ID }}
    b2-key: ${{ secrets.META_B2_KEY }}
    cf-key: ${{ secrets.META_CF_KEY }}
```