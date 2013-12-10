\\See errors.shen file

(defprolog mem
  X [X | _] <--;
  X [Y | Z] <-- (mem X Z);)  

\\The mem function above works fine. However
\\it is this mem function which, in conjunction with 
\\the new form of the prolog-macro, seems to cause problems
(defprolog mem
  X (mode [X | _] -) <--;
  X (mode [_ | Y] -) <-- (mem X Y);)

(prolog? (mem 1 [X | 2]) (return X))
