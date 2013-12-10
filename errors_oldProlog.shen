\\See errors.shen file
\\overwriting prolog-macro with old
\\version seems to get rid of errors.

(package shen []

\\This is the old prolog-macro
(define prolog-macro
  [prolog? | X] -> [intprolog (prolog-form X)]
  X -> X)

(define prolog-form
  X -> (cons_form (map (function cons_form) X)))

)
