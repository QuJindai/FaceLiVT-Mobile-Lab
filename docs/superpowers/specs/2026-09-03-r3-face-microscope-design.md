# R3 Face Microscope Design

## Goal
Turn the handset lab from a single mixed control screen into two explicit observable workflows: Enrollment Microscope and Recognition Microscope.

## Product structure
- One activity with two real page containers and a persistent two-tab switch: `录入显微镜` and `检测显微镜`.
- The camera preview remains local to the active page context; each page owns its controls and microscope panels.
- Enrollment builds a quality archive before committing templates.
- Recognition exposes the live decision path from pixels to final accept/reject.

## Enrollment Microscope
### Capture and quality archive
Each accepted sample stores:
- source/degraded dimensions and degradation profile
- face pixel size and face-area ratio
- sharpness, brightness, contrast, pose, landmark visibility, size quality, composite quality
- yaw, pitch, roll
- five-point landmark availability
- aligned 112x112 thumbnail
- XS/S/M embeddings

### Template microscope
For each model variant:
- quality-weighted centroid
- each sample cosine-to-centroid
- stability = mean cosine(sample, centroid)
- dispersion = mean(1 - cosine(sample, centroid))
- pairwise cosine matrix
- 2D PCA projection of sample embeddings plus centroid

### Enrollment formula chain
`Qi = .25 Qsharp + .15 Qlight + .10 Qcontrast + .20 Qpose + .15 Qlandmark + .15 Qsize`

`alpha_i = max(Qi, 0.05)`

`c = normalize(sum(alpha_i * f_i) / sum(alpha_i))`

`Sstable = mean(cos(f_i, c))`

`D = mean(1 - cos(f_i, c))`

`Pass = N>=5 AND Qavg>=0.55 AND Sstable>=0.70`

The page must show the numeric substitution, not only the symbolic formula.

## Recognition Microscope
### Live pipeline
Show explicit stages and timing:
`frame -> degrade -> detect -> 5pt align -> quality gate -> embed -> temporal fusion -> Top-K match -> decision`

### Live probe quality
Show sharpness, brightness, contrast, pose, landmark visibility, size quality, composite quality and yaw/pitch/roll.

### Match microscope
For the active model (or each model in compare mode):
- Top-3 identity similarities
- threshold line/value
- Top1-Top2 margin
- fused frame count
- current probe 2D position relative to stored template centroid when possible
- rolling similarity and quality trend

### Recognition formula chain
`sk = cos(f_probe, c_k)`

`k* = argmax(sk)`

`margin = s_top1 - s_top2`

`Accept = s_top1 >= Tid AND Qprobe >= Tq`

R3 keeps the existing user-controlled identity threshold and uses `Tq=0.35` as a visible quality gate. The UI shows actual values and which condition failed.

## Visualization rules
- Human-readable labels first; raw dimensions/vectors are secondary.
- No raw 512-number dumps on the main page.
- Similarity matrix, embedding projection, Top-K bars and temporal trend are custom lightweight Android Views with no chart dependency.
- Visualizations must remain readable on S24U portrait and respect status/navigation insets.

## Persistence
- Existing R1/R2 templates remain readable.
- R3 adds a quality archive store keyed by identity and sample index.
- New enrollment commits the quality-weighted centroid into the existing model-specific template keyspace only after the 5-sample microscope session completes.

## Non-goals for R3
- No cloud upload.
- No active liveness model.
- No training/fine-tuning on device.
- No requirement for the user to perform intermediate validation.