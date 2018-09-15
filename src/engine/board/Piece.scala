package engine.board

import engine.board.bitboards.Bitboard

/**
  * Created by melvic on 8/6/18.
  */
sealed trait PieceType
case object Pawn extends PieceType
case object Knight extends PieceType
case object Bishop extends PieceType
case object Rook extends PieceType
case object Queen extends PieceType
case object King extends PieceType

sealed trait Side {
  def opposite: Side = this match {
    case White => Black
    case Black => White
  }
}

case object White extends Side
case object Black extends Side

case class Piece(pieceType: PieceType, side: Side)

object Piece {
  def whiteOf(pieceType: PieceType) = Piece(pieceType, White)
  def blackOf(pieceType: PieceType) = Piece(pieceType, Black)

  lazy val types = Pawn :: Knight :: Bishop :: Rook :: Queen :: King :: Nil
  lazy val sides = White :: Black :: Nil

  implicit def pieceTypeToInt(pieceType: PieceType): Int =
    types.indexOf(pieceType) + Bitboard.PieceTypeOffset

  implicit def intToPieceType(i: Int): PieceType = types(i - Bitboard.PieceTypeOffset)

  implicit def pieceSideToInt(side: Side): Int = side match {
    case White => 0
    case Black => 1
  }

  implicit def intToSide(i: Int): Side = sides(i)
}
