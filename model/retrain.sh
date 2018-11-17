#!/usr/bin/env bash

source "conf.sh"

python -m scripts.retrain \
  --bottleneck_dir=tf_files/bottlenecks \
  --how_many_training_steps=4000 \
  --model_dir=tf_files/models/ \
  --summaries_dir=tf_files/training_summaries/"${ARCHITECTURE}" \
  --output_graph=tf_files/handgame_graph.pb \
  --output_labels=tf_files/handgame_labels.txt \
  --architecture="${ARCHITECTURE}" \
  --image_dir=tf_files/gesture_photos