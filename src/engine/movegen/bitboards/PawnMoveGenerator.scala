package engine.movegen.bitboards

import engine.board.Piece._
import engine.board.bitboards.Bitboard
import engine.board.bitboards.Bitboard._
import engine.board._
import engine.movegen._
import engine.movegen.bitboards.BitboardMoveGenerator._

/**
  * Created by melvic on 8/5/18.
  */
object PawnMoveGenerator extends BitboardMoveGenerator with PostShiftOneStep {
  type PawnMove = (U64, U64, Side) => U64

  def singlePush: PawnMove = { (pawns, emptySquares, sideToMove) =>
    // Move north first,
    val pushedNorth = north(pawns)

    // then south twice, if piece is black
    val optionalTwoRows = sideToMove << 4
    val board = pushedNorth >> optionalTwoRows

    // And make sure you land on an empty square
    board & emptySquares
  }

  def doublePush: PawnMove = { (pawns, emptySquares, sideToMove) =>
    val destinationMasks = List(
      0x00000000FF000000L,  // rank 4 (white's double push destination)
      0x000000FF00000000L   // rank 5 (black's double push destination)
    )

    val pushedPawns = singlePush(pawns, emptySquares, sideToMove)
    singlePush(pushedPawns, emptySquares, sideToMove) & destinationMasks(sideToMove)
  }

  def attack(northAttack: U64 => U64, southAttack: U64 => U64): PawnMove = {
    (pawns, opponents, sideToMove) =>
      val move = sideToMove match {
        case White => northAttack
        case _ => southAttack
      }
      move(pawns) & opponents
  }

  def attackEast: PawnMove = attack(northEast, southEast)
  def attackWest: PawnMove = attack(northWest, southWest)

  // TODO: Incorporate this.
  def enPassant: PawnMove = { (pawns, opponent, sideToMove) =>
    // Decide whether to move north or south based on the color
    val (setwiseOp, action): (SetwiseOp, U64 => U64) = sideToMove match {
      case White => (_ << _, north)
      case _ => (_ >> _, south)
    }

    val newOpponent = setwiseOp(pawns, Board.Size)

    // Decide whether to move east or west for the attack
    val attack = {
      val pawnPosition = Bitboard.oneBitIndex(pawns)
      val opponentPosition = Bitboard.oneBitIndex(opponent)
      action andThen (
        if (pawnPosition % Board.Size > opponentPosition % Board.Size) west
        else east
      )
    }

    attack(pawns) & newOpponent
  }

  /**
    * @param getTargets Determines the target square (empty, occupied by opponents, etc.).
    * @return A move generator of stream of (U64, MoveType) denoting the pawn moves.
    */
  def generatePawnMoves(moves: Stream[WithMove[PawnMove]],
                        getTargets: (Bitboard, Side) => U64): StreamGen[WithMove[U64]] = {
    case (bitboard, source, sideToMove) =>
      val pawns = Bitboard.singleBitset(source)
      moves flatMap { case move@(pawnMove, _) =>
        val moveBitset: PawnMove => U64 = _(pawns, getTargets(bitboard, sideToMove), sideToMove)

        // Rank == 7 or Rank == 0 (since White == 0 and Black == 1)
        val promote = (Bitboard.rankOf(source) + sideToMove) % Board.Size == 0

        if (promote)
          // Generate bitboards for each position promotions
          Stream(Knight, Bishop, Rook, Queen) map { officerType =>
            val promotionOptions = (pawnMove, PawnPromotion(Piece(officerType, sideToMove)))
            withMoveType(promotionOptions)(moveBitset)
          }
        else Stream(withMoveType(move)(moveBitset))
      }
  }

  def pushMoves = Stream((singlePush, Normal), (doublePush, DoublePawnPush))
  def attackMoves = Stream((attackEast, Attack), (attackWest, Attack))

  def generatePushes = generatePawnMoves(pushMoves, (board, side) => board.emptySquares)
  def generateAttacks = generatePawnMoves(attackMoves, (board, side) => board.opponents(side))

  def destinationBitsets: StreamGen[WithMove[U64]] = (bitboard, source, sideToMove) =>
    generatePushes(bitboard, source, sideToMove) ++ generateAttacks(bitboard, source, sideToMove)
}
