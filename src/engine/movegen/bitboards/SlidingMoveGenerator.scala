package engine.movegen.bitboards

import engine.board.bitboards.Bitboard.U64
import engine.board.bitboards.{Bitboard, Transformers}
import engine.board.{Board, Piece}
import engine.movegen.Attack

import scala.annotation.tailrec

/**
  * Created by melvic on 9/23/18.
  */
trait SlidingMoveGenerator extends BitboardMoveGenerator {
  /**
    * A mapping of a slider position and a bitset to a bitset
    */
  type Slide = (Int, U64) => U64

  object Masks {
    lazy val Files = Array(
      0x0101010101010101L,
      0x0202020202020202L,
      0x0404040404040404L,
      0x0808080808080808L,
      0x1010101010101010L,
      0x2020202020202020L,
      0x4040404040404040L,
      0x8080808080808080L)

    lazy val Ranks = Array(
      0x00000000000000ffL,
      0x000000000000ff00L,
      0x0000000000ff0000L,
      0x00000000ff000000L,
      0x000000ff00000000L,
      0x0000ff0000000000L,
      0x00ff000000000000L,
      0xff00000000000000L)

    // TODO: Consider optimizing this
    def diagonals(f: U64 => U64) = (0 until Board.Size * Board.Size).foldLeft(List[U64]()) { (acc, i) =>
      val bitboard = Bitboard.singleBitset(i)

      @tailrec
      def recurse(bitboard: U64, acc: U64): U64 =
        if (Bitboard.isEmptySet(bitboard)) acc
        else recurse(f(bitboard), acc | bitboard)

      recurse(bitboard, 0L) :: acc
    }

    lazy val oneStep = new PostShiftOneStep {}
    lazy val Diagonals = diagonals(oneStep.northEast) ++ diagonals(oneStep.southWest)
    lazy val AntiDiagonals = diagonals(oneStep.northWest) ++ diagonals(oneStep.southEast)
  }

  /**
    * Multiply the slider by 2 to move it one step closer to the blocker.
    * Then subtract the product from the board of occupied squares to
    * flip the zeroes between the slider and the blocker to ones (due
    * to borrowing), thereby turning the blocker into zero. Then xor the
    * result with the occupied squares to toggle the bits. The blocker
    * shall be set back to one, the slider to zero, the empty squares
    * between them to ones, and the rest to zeroes.
    */
  def positiveSlide: Slide = { (sliderPosition, occupied) =>
    val slider = Bitboard.singleBitset(sliderPosition)
    occupied ^ (occupied - 2 * slider)
  }

  /**
    * The "reversed" version of the algorithm for the positive ray.
    */
  def negativeSlide: Slide = { (sliderPosition, occupied) =>
    val transform = Transformers.rotate180

    val slider = Bitboard.singleBitset(sliderPosition)
    val reversedSlider = transform(slider)
    val reversedOccupied = transform(occupied)

    occupied ^ transform(reversedOccupied - 2 * reversedSlider)
  }

  def ray(slide: Slide)(masker: Int => U64): Slide = { (sliderPosition, occupied) =>
    val mask = masker(sliderPosition)
    val targetSquares = occupied & mask
    slide(sliderPosition, targetSquares) & mask
  }

  lazy val positiveRay: (Int => U64) => Slide = ray(positiveSlide)
  lazy val negativeRay: (Int => U64) => Slide = ray(negativeSlide)

  def fileMask(sliderPosition: Int) = Masks.Files(sliderPosition % Board.Size)

  def rankMask(sliderPosition: Int) = Masks.Ranks(sliderPosition / Board.Size)

  def diagonalMask(sliderPosition: Int) = Masks.Diagonals(sliderPosition)

  def antiDiagonalMask(sliderPosition: Int) = Masks.AntiDiagonals(sliderPosition)

  def moves: Stream[Slide]

  override def destinationBitsets: StreamGen[WithMove[U64]] = { (board, source, side) =>
    moves flatMap { slide =>
      val moveBitset = slide(source, board.occupied)
      val blocker = board.occupied & moveBitset

      // remove the blocker from the set of valid destinations if it's not an enemy and is not empty
      val validMoveBitSet = board(Bitboard.oneBitIndex(blocker)) match {
        case Some(Piece(_, blockerSide)) if blockerSide == side =>
          moveBitset ^ blocker
        case _ => moveBitset
      }

      Bitboard.serializeToStream(validMoveBitSet).map((_, Attack))
    }
  }
}