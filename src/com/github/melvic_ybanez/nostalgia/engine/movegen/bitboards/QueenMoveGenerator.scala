package com.github.melvic_ybanez.nostalgia.engine.movegen.bitboards

/**
  * Created by melvic on 12/17/18.
  */
object QueenMoveGenerator extends SlidingMoveGenerator {
  override def moves = BishopMoveGenerator.moves ++ RookMoveGenerator.moves
}