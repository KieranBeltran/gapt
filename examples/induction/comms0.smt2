; solve with: viper --treegrammar --qtys ""

(declare-datatypes () ((nat (o) (s (p nat)))))
(define-fun-rec plus ((x nat) (y nat)) nat
  (match y
    (case o x)
    (case (s y1) (s (plus x y1)))))
(assert-not (forall ((x nat))
  (= (plus x (s o)) (plus (s o) x))))
(check-sat)
