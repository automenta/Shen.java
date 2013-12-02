(load "shen/test/README.shen")
(load "shen/test/prolog.shen")
(prolog? (mem 1 [X | 2]) (return X))
