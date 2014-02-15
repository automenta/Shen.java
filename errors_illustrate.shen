\\See errors.shen file

(defprolog mem
  X [X | _] <--;
  X [Y | Z] <-- (mem X Z);)  

\*
The mem function above works fine. However
it is this mem function which, in conjunction with 
the new form of the prolog-macro, and the
"(return X)" which seems to cause the error.
Tracking the mem function shows that it returns
fine.
*\
(defprolog mem
  X (mode [X | _] -) <--;
  X (mode [_ | Y] -) <-- (mem X Y);)

\*
(defun mem (V542 V543 V544 V545) 
  (let Case 
   (let V531 (shen.lazyderef V543 V544) 
     (if (cons? V531) 
       (let X (hd V531) (do (shen.incinfs) (unify! X V542 V544 V545))) 
       false)) 
   (if (= Case false) 
         (let V532 (shen.lazyderef V543 V544) 
          (if (cons? V532) (let Y (tl V532) (do (shen.incinfs) (mem V542 Y V544 V545))) false)) 
         Case)))
*\

(prolog? (mem 1 [X | 2]) (return X))


\*

Note that the following works fine : 

(prolog? (mem 1 [X | 2]) (return [X]))

Also if you track mem, then the line above works fine. 
But as soon as you untrack, the error returns. To see
this do ; 

(track mem)

(prolog? (mem 1 [X | 2]) (return X))

(untrack mem)

(prolog? (mem 1 [X | 2]) (return X))

See also the post at : 

https://groups.google.com/d/msg/qilang/3DXJWo0hcRc/Q5iXoYaF-FsJ

*\
