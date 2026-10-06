# Weapon detector -- evaluation

**Weights:** `best.pt`  
**Dataset:** guns-knives (knife, pistol)  
**Split:** val (0 images)  
**Input size:** 416 px  

## Overall

| Metric | Value |
|---|---|
| mAP@50 | **0.589** |
| mAP@50-95 | **0.403** |
| Mean precision | 0.751 |
| Mean recall | 0.509 |

## Per class

| Class | Precision | Recall | AP@50 | AP@50-95 |
|---|---|---|---|---|
| knife | 0.806 | 0.345 | 0.452 | 0.268 |
| pistol | 0.697 | 0.673 | 0.725 | 0.538 |
