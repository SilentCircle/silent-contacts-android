#!/bin/sh

grep COMMIT_ID "${1}" | sed -e 's/^.*= "//' -e 's/";//'
