\*
See following two posts : 

https://groups.google.com/d/msg/qilang/3DXJWo0hcRc/wNUU5OKdDMkJ

https://groups.google.com/d/msg/qilang/9WxbCbxg8f4/dJsJtLmREkcJ

Note that you can only track functions which are not made 
external. For example if you add "bind" to the list below,
Shen complains that it is not a legitimate function name
*\

(package shen []

\\from prolog.shen
(define deref 
  [X | Y] ProcessN -> [(deref X ProcessN) | (deref Y ProcessN)]
  X ProcessN -> (if (pvar? X) 
                   (let Value (valvector X ProcessN)
                      (if (= Value -null-)
                         X
                         (deref Value ProcessN)))
                      X))

\\from prolog.shen           
(define lazyderef 
  X ProcessN -> (if (pvar? X) 
                  (let Value (valvector X ProcessN)
                     (if (= Value -null-)
                         X
                         (lazyderef Value ProcessN)))
                  X))
\*
\\from prolog.shen
(define bind 
  X Y ProcessN Continuation -> (do (bindv X Y ProcessN) 
                                   (let Result (thaw Continuation)
                                       (do (unbindv X ProcessN)
                                           Result))))
*\
)

(map (function track) [shen.deref shen.lazyderef])
