#!/usr/bin/env bash

source "conf.sh"

tflite_convert \
  --graph_def_file=tf_files/handgame_graph.pb \
  --output_file=tf_files/handgame_graph.lite \
  --input_format=TENSORFLOW_GRAPHDEF \
  --output_format=TFLITE \
  --input_shape=1,${IMAGE_SIZE},${IMAGE_SIZE},3 \
  --input_array=input \
  --output_array=final_result \
  --inference_type=FLOAT \
  --input_type=FLOAT