(defprolog mem
  X [X | _] <--;
  X [Y | Z] <-- (mem X Z);)  

\\This is the culprit
(defprolog mem
  X (mode [X | _] -) <--;
  X (mode [_ | Y] -) <-- (mem X Y);)

(prolog? (mem 1 [X | 2]) (return X))
