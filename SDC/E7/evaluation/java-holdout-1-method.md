# Independent Java E7 holdout 1

Created 2026-10-03 by an independent dataset author from the E7 scope specification and read-only local MySQL product data. The author did not read E7 implementation, prompts, tests, earlier evaluation questions, or earlier evaluation results.

The JSON contains 20 normal, 8 adversarial, and 4 out-of-domain Chinese inputs. Normal cases expect `OK`, including valid queries with no matches. All other cases expect `UNSUPPORTED` and an empty ID array. JSON integers represent Java long product IDs. Page size is 100; the visible dataset at authoring contains 20 products.

Expected IDs were initially enumerated from `id,name,price,stock` of `mall.pms_product`, constrained by `delete_status=0 AND publish_status=1`. Each of the 20 supported cases was then verified with its own direct read-only SQL predicate against that same visibility restriction. Name matching used `LOCATE(literal,name)>0`, so percent and underscore are ordinary characters. Price and stock comparisons used strict `<`. Combined filters used `AND`. No application translator or application-generated SQL was used as the reference oracle. UTF-8 client encoding was set explicitly.

Verification passed: 32 distinct IDs; exact JSON field set; required 20/8/4 category counts; status/category consistency; sorted unique Java-long-compatible result IDs; all 20 SQL result sets exactly matched their independently specified expected IDs. Database data was not modified. Model behavior has not been executed or scored by the dataset author.

The cases must stay outside implementation and prompt development review until the implementation is settled and the model evaluation has run. This is a one-time holdout handling instruction, not a new application gate or contract. Expected IDs depend on the local demo database state at authoring; ordinary data changes can invalidate them.
