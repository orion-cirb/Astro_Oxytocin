# Astro_Oxytocin

* **Developed for:** Maïna
* **Team:** Rouach
* **Date:** Avril 2023
* **Software:** Fiji


### Images description

3D images taken with a x60 objective

3 channels:
  1. *Alexa Fluor 405:* Nuclei
  2. *Alexa Fluor 491:* Oxytocin receptor foci
  3. *Alexa Fluor 561:* Astrocytes

### Plugin description

* Detect nuclei and astrocytes somas with Cellpose
* Only keep astrocytes overlapping with a nucleus 
* In each astrocyte, detect oxytocin receptor foci with Stardist

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **Cellpose** conda environment + *cyto2* model
* **StarDist** conda environment + *fociRNA-1.2.zip* (homemade) model

### Version history

:warning: Plugin not finished, bad detection of astrocytes. :warning:

